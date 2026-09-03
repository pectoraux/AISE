package com.aise.field.sync

import com.aise.field.capture.engine.LocalFileStore
import com.aise.field.contracts.dto.*
import com.aise.field.sync.client.CaptureUploadClient
import com.aise.field.sync.client.UploadResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UploadClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: CaptureUploadClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = CaptureUploadClient(mockWebServer.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createSampleManifestAndAsset(fileContentBytes: ByteArray): Pair<CapturePackageContractDto, Pair<PackageAssetDto, java.io.File>> {
        val file = tempFolder.newFile("test_photo.jpg")
        file.writeBytes(fileContentBytes)
        val hash = LocalFileStore.computeSha256(fileContentBytes)

        val assetDto = PackageAssetDto(
            assetId = "asset-001",
            assetType = ContractAssetType.PHOTO,
            relativePath = "photos/test_photo.jpg",
            contentHash = hash,
            byteSize = fileContentBytes.size.toLong(),
            mimeType = "image/jpeg",
            acquisition = AcquisitionMetadataDto(
                capturedAt = "2026-09-03T12:00:00Z"
            )
        )

        val manifestDto = CapturePackageContractDto(
            contractVersion = "1.0",
            packageId = "pkg-001",
            sessionId = "sess-001",
            projectId = "proj-001",
            createdAt = "2026-09-03T12:00:00Z",
            totalByteSize = fileContentBytes.size.toLong(),
            assets = listOf(assetDto)
        )

        return Pair(manifestDto, Pair(assetDto, file))
    }

    @Test
    fun uploadAsset_success_acceptedOutcome() {
        val (manifest, assetAndFile) = createSampleManifestAndAsset("VALID_IMAGE_DATA".toByteArray())
        val (asset, file) = assetAndFile

        val responseJson = """
            {
                "contractVersion": "1.0",
                "assetId": "asset-001",
                "outcome": "ACCEPTED",
                "receivedHash": "${asset.contentHash}"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val response = client.uploadAsset(manifest, asset, file)

        assertTrue(response is UploadResponse.Success)
        val success = response as UploadResponse.Success
        assertEquals(ContractUploadOutcome.ACCEPTED, success.result.outcome)
        assertEquals("asset-001", success.result.assetId)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/capture/upload", recordedRequest.path)
        assertTrue(recordedRequest.headers["Content-Type"]!!.startsWith("multipart/form-data"))
    }

    @Test
    fun uploadAsset_success_duplicateOutcome() {
        val (manifest, assetAndFile) = createSampleManifestAndAsset("DUPLICATE_IMAGE_DATA".toByteArray())
        val (asset, file) = assetAndFile

        val responseJson = """
            {
                "contractVersion": "1.0",
                "assetId": "asset-001",
                "outcome": "DUPLICATE",
                "receivedHash": "${asset.contentHash}",
                "duplicateOf": "existing-canonical-asset-999"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val response = client.uploadAsset(manifest, asset, file)

        assertTrue(response is UploadResponse.Success)
        val success = response as UploadResponse.Success
        assertEquals(ContractUploadOutcome.DUPLICATE, success.result.outcome)
        assertEquals("existing-canonical-asset-999", success.result.duplicateOf)
    }

    @Test(expected = IllegalStateException::class)
    fun evidenceCorruptionTest_contentHashMismatch_failsClosedLocally() {
        val (manifest, assetAndFile) = createSampleManifestAndAsset("ORIGINAL_DATA".toByteArray())
        val (asset, file) = assetAndFile

        // Corrupt local file after manifest generation
        file.writeBytes("CORRUPTED_DATA_MODIFIED".toByteArray())

        // Must throw IllegalStateException locally without making network request
        try {
            client.uploadAsset(manifest, asset, file)
        } finally {
            assertEquals(0, mockWebServer.requestCount) // Zero network requests transmitted
        }
    }

    @Test(expected = IllegalStateException::class)
    fun evidenceCorruptionTest_byteSizeMismatch_failsClosedLocally() {
        val (manifest, assetAndFile) = createSampleManifestAndAsset("TEST_DATA".toByteArray())
        val (asset, file) = assetAndFile

        // Corrupt asset DTO byteSize
        val corruptAsset = asset.copy(byteSize = 999999L)

        // Must throw IllegalStateException locally without making network request
        try {
            client.uploadAsset(manifest, corruptAsset, file)
        } finally {
            assertEquals(0, mockWebServer.requestCount) // Zero network requests transmitted
        }
    }
}
