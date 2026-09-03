package com.aise.field.contracts

import com.aise.field.contracts.dto.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SharedContractValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun validateFixture_01_project_full() {
        val content = ContractFixtureLoader.readFixtureText("project.full.json")
        val dto = json.decodeFromString<ProjectContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals("5f0c9d8e-3a47-4b21-9f6a-8c2d1e4b7a30", dto.projectId)
        assertEquals("Warehouse B — mezzanine as-built", dto.name)
        assertNotNull(dto.description)
        assertEquals("2026-09-01T08:30:00Z", dto.createdAt)
        assertEquals("2026-09-02T10:15:00Z", dto.updatedAt)
    }

    @Test
    fun validateFixture_02_capture_session_full() {
        val content = ContractFixtureLoader.readFixtureText("capture-session.full.json")
        val dto = json.decodeFromString<CaptureSessionContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals("2d7e8f4a-1c9b-46f3-a5e8-93d2c7b0e615", dto.sessionId)
        assertEquals("5f0c9d8e-3a47-4b21-9f6a-8c2d1e4b7a30", dto.projectId)
        assertEquals(ContractCaptureIntent.AS_BUILT, dto.intent)
        assertEquals(ContractAssuranceProfile.HIGH_ASSURANCE, dto.assuranceProfile)
        assertEquals(ContractSessionStatus.IN_PROGRESS, dto.status)
        assertEquals("user-7f3k", dto.operatorRef)
        assertNotNull(dto.notes)
    }

    @Test
    fun validateFixture_03_capture_session_minimal() {
        val content = ContractFixtureLoader.readFixtureText("capture-session.minimal.json")
        val dto = json.decodeFromString<CaptureSessionContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractCaptureIntent.INSPECTION, dto.intent)
        assertEquals(ContractAssuranceProfile.STANDARD, dto.assuranceProfile)
        assertEquals(ContractSessionStatus.DRAFT, dto.status)
        assertNull(dto.updatedAt)
        assertNull(dto.operatorRef)
        assertNull(dto.notes)
    }

    @Test
    fun validateFixture_04_capture_package_full() {
        val content = ContractFixtureLoader.readFixtureText("capture-package.full.json")
        val dto = json.decodeFromString<CapturePackageContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals("9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", dto.packageId)
        assertEquals("sha256", dto.checksumAlgorithm)
        assertEquals(23267845L, dto.totalByteSize)
        assertEquals(3, dto.assets.size)

        val photoAsset = dto.assets[0]
        assertEquals(ContractAssetType.PHOTO, photoAsset.assetType)
        assertEquals("photos/IMG_0001.jpg", photoAsset.relativePath)
        assertEquals("image/jpeg", photoAsset.mimeType)
        assertNotNull(photoAsset.acquisition.geolocation)
        assertEquals(5.6037, photoAsset.acquisition.geolocation!!.latitude, 0.0001)

        val depthAsset = dto.assets[1]
        assertEquals(ContractAssetType.DEPTH, depthAsset.assetType)
        assertEquals("depth_0", depthAsset.acquisition.sensorRef)

        val metaAsset = dto.assets[2]
        assertEquals(ContractAssetType.METADATA, metaAsset.assetType)
    }

    @Test
    fun validateFixture_05_upload_request() {
        val content = ContractFixtureLoader.readFixtureText("upload-request.json")
        val dto = json.decodeFromString<UploadRequestContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals("8b2e4c6a-9d0f-4e1a-b3c5-7d9e1f3a5c7e", dto.idempotencyKey)
        assertEquals(4825311L, dto.byteSize)
        assertNull(dto.part)
    }

    @Test
    fun validateFixture_06_upload_result_accepted() {
        val content = ContractFixtureLoader.readFixtureText("upload-result.accepted.json")
        val dto = json.decodeFromString<UploadResultContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractUploadOutcome.ACCEPTED, dto.outcome)
        assertNull(dto.duplicateOf)
    }

    @Test
    fun validateFixture_07_upload_result_duplicate() {
        val content = ContractFixtureLoader.readFixtureText("upload-result.duplicate.json")
        val dto = json.decodeFromString<UploadResultContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractUploadOutcome.DUPLICATE, dto.outcome)
        assertEquals("1e2f3a4b-5c6d-4e7f-8a9b-0c1d2e3f4a5b", dto.duplicateOf)
        assertNotNull(dto.note)
    }

    @Test
    fun validateFixture_08_sync_error_retryable() {
        val content = ContractFixtureLoader.readFixtureText("sync-error.retryable.json")
        val dto = json.decodeFromString<SyncErrorContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractSyncErrorCode.RATE_LIMITED, dto.code)
        assertTrue(dto.retryable)
        assertEquals(30000L, dto.retryAfterMs)
        assertNotNull(dto.details)
    }

    @Test
    fun validateFixture_09_sync_error_fatal() {
        val content = ContractFixtureLoader.readFixtureText("sync-error.fatal.json")
        val dto = json.decodeFromString<SyncErrorContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractSyncErrorCode.IDEMPOTENCY_CONFLICT, dto.code)
        assertFalse(dto.retryable)
        assertNotNull(dto.details)
    }

    @Test
    fun validateFixture_10_sync_error_version_unsupported() {
        val content = ContractFixtureLoader.readFixtureText("sync-error.version-unsupported.json")
        val dto = json.decodeFromString<SyncErrorContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(ContractSyncErrorCode.CONTRACT_VERSION_UNSUPPORTED, dto.code)
        assertFalse(dto.retryable)
        assertNotNull(dto.details)
    }

    @Test
    fun validateFixture_11_model_version_full() {
        val content = ContractFixtureLoader.readFixtureText("model-version.full.json")
        val dto = json.decodeFromString<ModelVersionContractDto>(content)

        assertEquals("1.0", dto.contractVersion)
        assertEquals(3, dto.version)
        assertEquals(2, dto.parentVersion)
    }

    @Test
    fun validateFixture_12_model_object_ref() {
        val content = ContractFixtureLoader.readFixtureText("model-object-ref.json")
        val dto = json.decodeFromString<ModelObjectRefContractDto>(content)

        assertEquals("3c5d7e9f-1a2b-4c3d-8e7f-9a0b1c2d3e4f", dto.modelId)
        assertEquals(3, dto.version)
        assertEquals("element:wall-south-01", dto.objectId)
    }

    @Test
    fun validateFixture_13_measurement_transport_full() {
        val content = ContractFixtureLoader.readFixtureText("measurement.transport.full.json")
        val dto = json.decodeFromString<MeasurementTransportDto>(content)

        assertEquals(ContractMeasurementKind.measurement, dto.kind)
        assertEquals(2.417, dto.value, 0.0001)
        assertEquals("m", dto.unit)
        assertNotNull(dto.uncertainty)
        assertEquals(0.008, dto.uncertainty!!.plusMinus, 0.0001)
        assertEquals("m", dto.uncertainty!!.unit)
        assertEquals("laser_distance_meter", dto.method)
    }

    @Test
    fun validateFixture_14_measurement_transport_estimate() {
        val content = ContractFixtureLoader.readFixtureText("measurement.transport.estimate.json")
        val dto = json.decodeFromString<MeasurementTransportDto>(content)

        assertEquals(ContractMeasurementKind.estimate, dto.kind)
        assertEquals(2.4, dto.value, 0.0001)
        assertEquals("m", dto.unit)
        assertEquals(0.62, dto.confidence!!, 0.0001)
        assertNull(dto.uncertainty)
        assertEquals("ai_proposal", dto.method)
    }

    @Test
    fun validateFixture_15_evidence_vocabulary() {
        val content = ContractFixtureLoader.readFixtureText("evidence-vocabulary.json")
        val dto = json.decodeFromString<EpistemicTransportDto>(content)

        assertEquals(ContractEpistemicState.INFERRED, dto.epistemicState)
        assertEquals(ContractObservationPresence.OCCLUDED, dto.observationPresence)
    }
}
