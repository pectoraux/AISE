package com.aise.field.contracts

import com.aise.field.contracts.adapter.ContractTimestampAdapter
import com.aise.field.contracts.adapter.ContractVersionResult
import com.aise.field.contracts.adapter.ContractVersionValidator
import com.aise.field.contracts.adapter.SyncRetryPolicy
import com.aise.field.contracts.dto.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NegativeAndCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun versionValidator_acceptsSameMajor_rejectsCrossMajor() {
        val res1 = ContractVersionValidator.validateVersion("1.0")
        assertTrue(res1 is ContractVersionResult.Supported)
        assertEquals(1, (res1 as ContractVersionResult.Supported).major)
        assertEquals(0, res1.minor)

        val res2 = ContractVersionValidator.validateVersion("1.5")
        assertTrue(res2 is ContractVersionResult.Supported)
        assertEquals(1, (res2 as ContractVersionResult.Supported).major)
        assertEquals(5, res2.minor)

        val res3 = ContractVersionValidator.validateVersion("2.0")
        assertTrue(res3 is ContractVersionResult.Unsupported)
        assertEquals("2.0", (res3 as ContractVersionResult.Unsupported).requestedVersion)

        val res4 = ContractVersionValidator.validateVersion("invalid")
        assertTrue(res4 is ContractVersionResult.Unsupported)
    }

    @Test
    fun unknownEnumValues_mapToUnknownSentinel() {
        val payload = """
            {
              "contractVersion": "1.0",
              "sessionId": "2d7e8f4a-1c9b-46f3-a5e8-93d2c7b0e615",
              "projectId": "5f0c9d8e-3a47-4b21-9f6a-8c2d1e4b7a30",
              "intent": "FUTURE_INTENT_V2",
              "assuranceProfile": "FUTURE_PROFILE_99",
              "status": "FUTURE_STATUS",
              "createdAt": "2026-09-03T07:05:00Z"
            }
        """.trimIndent()

        val dto = json.decodeFromString<CaptureSessionContractDto>(payload)
        assertEquals(ContractCaptureIntent.UNKNOWN, dto.intent)
        assertEquals(ContractAssuranceProfile.UNKNOWN, dto.assuranceProfile)
        assertEquals(ContractSessionStatus.UNKNOWN, dto.status)
    }

    @Test
    fun unknownFields_toleratedInNewerMinorPayloads() {
        val payload = """
            {
              "contractVersion": "1.1",
              "projectId": "5f0c9d8e-3a47-4b21-9f6a-8c2d1e4b7a30",
              "name": "Warehouse B",
              "createdAt": "2026-09-01T08:30:00Z",
              "futureFeatureMetadata": {
                "aiAnnotationVersion": "2.4"
              },
              "futureExperimentalFlag": true
            }
        """.trimIndent()

        val dto = json.decodeFromString<ProjectContractDto>(payload)
        assertEquals("1.1", dto.contractVersion)
        assertEquals("Warehouse B", dto.name)
    }

    @Test
    fun timestampConversion_losslessAtMillisecondPrecision() {
        val originalRfc3339 = "2026-09-03T07:40:00.123Z"
        val epochMillis = ContractTimestampAdapter.parseRfc3339ToEpochMillis(originalRfc3339)
        val formattedRfc3339 = ContractTimestampAdapter.epochMillisToRfc3339(epochMillis)

        assertEquals(originalRfc3339, formattedRfc3339)
    }

    @Test
    fun epistemicVocabulary_hasNoConfirmedAbsentMember() {
        val epistemicNames = ContractEpistemicState.entries.map { it.name }
        val presenceNames = ContractObservationPresence.entries.map { it.name }

        assertFalse("Epistemic vocabulary must not contain CONFIRMED_ABSENT", epistemicNames.contains("CONFIRMED_ABSENT"))
        assertFalse("Observation presence must not contain CONFIRMED_ABSENT", presenceNames.contains("CONFIRMED_ABSENT"))
    }

    @Test
    fun measurementVsEstimate_areDistinctAndSeparateFromUncertainty() {
        val measurementPayload = """
            {
              "kind": "measurement",
              "value": 5.0,
              "unit": "m",
              "uncertainty": {
                "plusMinus": 0.01,
                "unit": "m"
              }
            }
        """.trimIndent()

        val estimatePayload = """
            {
              "kind": "estimate",
              "value": 5.0,
              "unit": "m",
              "confidence": 0.85
            }
        """.trimIndent()

        val measurement = json.decodeFromString<MeasurementTransportDto>(measurementPayload)
        val estimate = json.decodeFromString<MeasurementTransportDto>(estimatePayload)

        assertEquals(ContractMeasurementKind.measurement, measurement.kind)
        assertNotNull(measurement.uncertainty)
        assertNull(measurement.confidence)

        assertEquals(ContractMeasurementKind.estimate, estimate.kind)
        assertNull(estimate.uncertainty)
        assertEquals(0.85, estimate.confidence!!, 0.0001)
    }

    @Test
    fun syncRetryPolicy_drivenStrictlyByData() {
        val retryableError = SyncErrorContractDto(
            contractVersion = "1.0",
            code = ContractSyncErrorCode.RATE_LIMITED,
            message = "Rate limit exceeded",
            retryable = true,
            retryAfterMs = 15000L
        )

        val fatalError = SyncErrorContractDto(
            contractVersion = "1.0",
            code = ContractSyncErrorCode.IDEMPOTENCY_CONFLICT,
            message = "Key reused with different content hash",
            retryable = false
        )

        val retryDecision = SyncRetryPolicy.evaluateRetry(retryableError)
        assertTrue(retryDecision.retryable)
        assertEquals(15000L, retryDecision.retryAfterMs)

        val fatalDecision = SyncRetryPolicy.evaluateRetry(fatalError)
        assertFalse(fatalDecision.retryable)
        assertNull(fatalDecision.retryAfterMs)
    }
}
