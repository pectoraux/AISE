package com.aise.field.sync.client

import com.aise.field.capture.engine.LocalFileStore
import com.aise.field.contracts.dto.CapturePackageContractDto
import com.aise.field.contracts.dto.PackageAssetDto
import com.aise.field.contracts.dto.SyncErrorContractDto
import com.aise.field.contracts.dto.UploadRequestContractDto
import com.aise.field.contracts.dto.UploadResultContractDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class UploadResponse {
    data class Success(val result: UploadResultContractDto) : UploadResponse()
    data class Failure(val error: SyncErrorContractDto) : UploadResponse()
}

class CaptureUploadClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun uploadAsset(
        manifest: CapturePackageContractDto,
        asset: PackageAssetDto,
        localFile: File
    ): UploadResponse {
        // 1. Pre-Upload Evidence Validation
        if (!localFile.exists()) {
            throw IllegalStateException("Evidence file does not exist at ${localFile.absolutePath} for asset ${asset.assetId}")
        }

        if (localFile.length() != asset.byteSize) {
            throw IllegalStateException("Evidence size mismatch for asset ${asset.assetId}: expected ${asset.byteSize}, found ${localFile.length()}")
        }

        val calculatedHash = LocalFileStore.computeSha256(localFile.readBytes())
        if (!calculatedHash.equals(asset.contentHash, ignoreCase = true)) {
            throw IllegalStateException("Evidence SHA-256 mismatch for asset ${asset.assetId}: expected ${asset.contentHash}, calculated $calculatedHash")
        }

        // 2. Construct UploadRequestEnvelope
        val idempotencyKey = UUID.nameUUIDFromBytes("${manifest.packageId}_${asset.assetId}".toByteArray()).toString()
        val uploadEnvelope = UploadRequestContractDto(
            contractVersion = "1.0",
            sessionId = manifest.sessionId,
            assetId = asset.assetId,
            idempotencyKey = idempotencyKey,
            contentHash = asset.contentHash,
            byteSize = asset.byteSize
        )

        val envelopeJson = json.encodeToString(UploadRequestContractDto.serializer(), uploadEnvelope)

        // 3. Construct Multipart Request Body
        val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
        val mediaTypeBinary = (asset.mimeType ?: "application/octet-stream").toMediaType()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("envelope", "envelope.json", envelopeJson.toRequestBody(mediaTypeJson))
            .addFormDataPart("file", asset.relativePath, localFile.asRequestBody(mediaTypeBinary))
            .build()

        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/capture/upload")
            .post(requestBody)
            .build()

        // 4. Execute HTTP Request
        return try {
            val httpResponse = httpClient.newCall(httpRequest).execute()
            val responseBodyStr = httpResponse.body?.string() ?: ""

            if (httpResponse.isSuccessful) {
                val uploadResult = json.decodeFromString(UploadResultContractDto.serializer(), responseBodyStr)
                UploadResponse.Success(uploadResult)
            } else {
                val syncError = try {
                    json.decodeFromString(SyncErrorContractDto.serializer(), responseBodyStr)
                } catch (e: Exception) {
                    val code = httpResponse.code
                    SyncErrorContractDto(
                        contractVersion = "1.0",
                        code = com.aise.field.contracts.dto.ContractSyncErrorCode.UNKNOWN,
                        message = "HTTP error $code: $responseBodyStr",
                        retryable = code >= 500 || code == 429,
                        retryAfterMs = if (code == 429) 60000L else null
                    )
                }
                UploadResponse.Failure(syncError)
            }
        } catch (e: Exception) {
            val syncError = SyncErrorContractDto(
                contractVersion = "1.0",
                code = com.aise.field.contracts.dto.ContractSyncErrorCode.SERVICE_UNAVAILABLE,
                message = e.message ?: "Network error",
                retryable = true,
                retryAfterMs = 15000L
            )
            UploadResponse.Failure(syncError)
        }
    }
}
