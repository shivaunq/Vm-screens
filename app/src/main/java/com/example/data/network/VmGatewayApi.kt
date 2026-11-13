package com.example.data.network

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GatewayHealthResponse(
    val ok: Boolean = false,
    val activeEmulators: Int = 0,
    val hostLabel: String = "",
    val version: String = ""
)

@JsonClass(generateAdapter = true)
data class GatewayLifecycleRequest(
    val vmId: String,
    val action: String
)

@JsonClass(generateAdapter = true)
data class GatewayUrlDispatchRequest(
    val vmIds: List<String>,
    val url: String,
    val targetPackage: String
)

@JsonClass(generateAdapter = true)
data class GatewayLaunchAppRequest(
    val vmIds: List<String>,
    val packageName: String,
    val appName: String
)

@JsonClass(generateAdapter = true)
data class GatewayInstallUploadedRequest(
    val uploadId: String,
    val vmIds: List<String>,
    val packageName: String,
    val appName: String,
    val openAfterInstall: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GatewayAppActionRequest(
    val vmIds: List<String>,
    val action: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSourceUrl: String,
    val sessionProfilePrefix: String
)

@JsonClass(generateAdapter = true)
data class GatewayDispatchResultItem(
    val vmId: String,
    val status: String,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class GatewayDispatchResponse(
    val accepted: Boolean = false,
    val message: String = "",
    val results: List<GatewayDispatchResultItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GatewayVmIdsRequest(
    val vmIds: List<String>
)

@JsonClass(generateAdapter = true)
data class GatewayClearSessionRequest(
    val vmIds: List<String>,
    val packageName: String,
    val openAfterClear: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GatewayInputTextRequest(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GatewayVmTelemetryItem(
    val vmId: String,
    val status: String = "REGISTERED",
    val connectivity: String = "WIFI",
    val ipAddress: String = "",
    val androidVersion: String = "",
    val batteryPercent: Long? = null,
    val signedInGmail: String = ""
)

@JsonClass(generateAdapter = true)
data class GatewayVmStatusResponse(
    val vms: List<GatewayVmTelemetryItem> = emptyList()
)

interface VmGatewayService {
    @GET
    suspend fun checkHealth(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?
    ): Response<GatewayHealthResponse>

    @POST
    suspend fun sendLifecycleCommand(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayLifecycleRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun dispatchUrls(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayUrlDispatchRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun manageApps(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayAppActionRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun launchApp(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayLaunchAppRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun installUploaded(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayInstallUploadedRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun openGoogleSignIn(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayVmIdsRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun clearAppSession(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayClearSessionRequest
    ): Response<GatewayDispatchResponse>

    @POST
    suspend fun sendInputText(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?,
        @Body body: GatewayInputTextRequest
    ): Response<GatewayDispatchResponse>

    @GET
    suspend fun getVmStatuses(
        @Url fullUrl: String,
        @Header("Authorization") bearerAuth: String?
    ): Response<GatewayVmStatusResponse>
}

class VmGatewayClient {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val api: VmGatewayService = retrofit.create(VmGatewayService::class.java)

    private fun authHeader(token: String): String? =
        token.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

    private fun normalizeEndpoint(baseUrl: String, path: String): String? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (
            !trimmed.startsWith("https://") &&
            !trimmed.startsWith("http://10.0.2.2") &&
            !trimmed.startsWith("http://127.0.0.1") &&
            !trimmed.startsWith("http://localhost")
        ) {
            return null
        }
        return "$trimmed$path"
    }

    suspend fun pingHealth(baseUrl: String, bearerToken: String): Result<GatewayHealthResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/health")
            ?: return Result.failure(IllegalArgumentException("Gateway URL must use https:// (or local dev 10.0.2.2)"))
        return runCatching {
            val response = api.checkHealth(url, authHeader(bearerToken))
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("HTTP ${response.code()}: ${response.message()}")
            }
        }
    }

    suspend fun sendLifecycle(
        baseUrl: String,
        bearerToken: String,
        vmId: String,
        action: String
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/vms/$vmId/lifecycle")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.sendLifecycleCommand(
                url,
                authHeader(bearerToken),
                GatewayLifecycleRequest(vmId = vmId, action = action)
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun dispatchUrls(
        baseUrl: String,
        bearerToken: String,
        vmIds: List<String>,
        targetUrl: String,
        targetPackage: String
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/urls/dispatch")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.dispatchUrls(
                url,
                authHeader(bearerToken),
                GatewayUrlDispatchRequest(vmIds = vmIds, url = targetUrl, targetPackage = targetPackage)
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun manageApps(
        baseUrl: String,
        bearerToken: String,
        request: GatewayAppActionRequest
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/apps/manage")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.manageApps(url, authHeader(bearerToken), request)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun launchApp(
        baseUrl: String,
        bearerToken: String,
        vmIds: List<String>,
        packageName: String,
        appName: String
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/apps/launch")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.launchApp(
                url,
                authHeader(bearerToken),
                GatewayLaunchAppRequest(vmIds = vmIds, packageName = packageName, appName = appName)
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun uploadPhoneAppAndInstall(
        baseUrl: String,
        bearerToken: String,
        apkFiles: List<File>,
        vmIds: List<String>,
        packageName: String,
        appName: String,
        openAfterInstall: Boolean
    ): Result<GatewayDispatchResponse> = withContext(Dispatchers.IO) {
        val installUrl = normalizeEndpoint(baseUrl, "/api/apps/install-uploaded")
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        if (apkFiles.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No readable APK files found on phone for $packageName"))
        }
        val uploadId = UUID.randomUUID().toString().replace("-", "").take(16)
        val auth = authHeader(bearerToken)
        runCatching {
            val octetType = "application/octet-stream".toMediaType()
            apkFiles.forEachIndexed { idx, file ->
                val rawName = file.name.takeIf { it.endsWith(".apk") } ?: "part_${idx}.apk"
                val encodedName = URLEncoder.encode(rawName, "UTF-8")
                val uploadUrl = normalizeEndpoint(baseUrl, "/api/apps/upload-apk?uploadId=$uploadId&fileName=$encodedName")
                    ?: error("Invalid upload URL")
                val reqBuilder = Request.Builder()
                    .url(uploadUrl)
                    .post(file.asRequestBody(octetType))
                if (auth != null) {
                    reqBuilder.header("Authorization", auth)
                }
                okHttp.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("APK upload failed (HTTP ${resp.code})")
                    }
                }
            }

            val response = api.installUploaded(
                installUrl,
                auth,
                GatewayInstallUploadedRequest(
                    uploadId = uploadId,
                    vmIds = vmIds,
                    packageName = packageName,
                    appName = appName,
                    openAfterInstall = openAfterInstall
                )
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun openGoogleSignIn(
        baseUrl: String,
        bearerToken: String,
        vmIds: List<String>
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/accounts/open-signin")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.openGoogleSignIn(
                url,
                authHeader(bearerToken),
                GatewayVmIdsRequest(vmIds = vmIds)
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun clearAppSession(
        baseUrl: String,
        bearerToken: String,
        vmIds: List<String>,
        packageName: String,
        openAfterClear: Boolean = true
    ): Result<GatewayDispatchResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/apps/clear-session")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.clearAppSession(
                url,
                authHeader(bearerToken),
                GatewayClearSessionRequest(
                    vmIds = vmIds,
                    packageName = packageName,
                    openAfterClear = openAfterClear
                )
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun sendRemoteText(
        baseUrl: String,
        bearerToken: String,
        vmId: String,
        text: String
    ): Result<GatewayDispatchResponse> {
        val encodedVm = URLEncoder.encode(vmId, "UTF-8")
        val url = normalizeEndpoint(baseUrl, "/api/vms/$encodedVm/input-text")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.sendInputText(
                url,
                authHeader(bearerToken),
                GatewayInputTextRequest(text = text.replace("\n", "").replace("\r", "").take(200))
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun fetchVmStatuses(
        baseUrl: String,
        bearerToken: String
    ): Result<GatewayVmStatusResponse> {
        val url = normalizeEndpoint(baseUrl, "/api/vms/status")
            ?: return Result.failure(IllegalArgumentException("Invalid or insecure gateway URL"))
        return runCatching {
            val response = api.getVmStatuses(url, authHeader(bearerToken))
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                error("Gateway HTTP ${response.code()}")
            }
        }
    }

    suspend fun fetchScreenBitmapBytes(
        baseUrl: String,
        bearerToken: String,
        vmId: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val encodedVm = URLEncoder.encode(vmId, "UTF-8")
        val url = normalizeEndpoint(baseUrl, "/api/vms/$encodedVm/screen.png") ?: return@withContext null
        val auth = authHeader(bearerToken)
        runCatching {
            val reqBuilder = Request.Builder().url(url).get()
            if (auth != null) {
                reqBuilder.header("Authorization", auth)
            }
            okHttp.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }
}
