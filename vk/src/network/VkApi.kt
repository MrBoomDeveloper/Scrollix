package com.mrboomdev.scrollix.data.vk.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.model.SessionException

internal const val VkApiVersion = "5.199"
private const val VkApiUrl = "https://api.vk.com/method"
private const val VkUploadMultipartBoundaryPrefix = "----ScrollixVk"

/** Error envelope returned by `api.vk.com`. */
internal class VkApiException(
    val apiCode: Int,
    val reason: String,
    /** Set when VK wants a captcha solved before the call is repeated. */
    val captchaSid: String? = null,
    val captchaImageUrl: String? = null
) : SessionException(
    code = vkSessionErrorCode(apiCode, reason),
    message = reason,
    retryable = vkSessionErrorCode(apiCode, reason).retryable || apiCode == 6 || apiCode == 10
) {
    /** 5 = invalid token, 28 = application authorization failed. */
    val isAuthError: Boolean get() = apiCode == 5 || apiCode == 28

    /** 9 = too many requests per second. */
    val isFlood: Boolean get() = apiCode == 9

    /** 14 = captcha needed; 17 = validation required. */
    val needsCaptcha: Boolean get() = apiCode == 14

    /** 6 = too many requests per second (legacy), 10 = internal server error. */
    val isRetryable: Boolean get() = retryable
}

/**
 * Thin client over VK's HTTP API.
 *
 * Every call is a form POST carrying `access_token` and `v`. The response is
 * either `{"response": …}` or `{"error": {...}}`; the error envelope becomes a
 * [VkApiException] so callers can tell an expired token from throttling.
 */
internal class VkApi(
    var token: String,
    private val json: Json
) {
    suspend fun call(
        method: String,
        params: Map<String, String> = emptyMap()
    ): JsonElement {
        AppLog.protocol("vk", "out method=$method", params.toString())

        val response = try {
            request(method, params)
        } catch (exception: CancellationException) {
            throw exception
        }

        AppLog.protocol("vk", "in method=$method", response)

        val root = response?.let { body ->
            runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        } ?: throw VkApiException(-1, "VK did not answer $method")

        root["error"]?.let { element ->
            val error = element.jsonObject
            throw VkApiException(
                apiCode = error["error_code"].asLongOrNull()?.toInt() ?: -1,
                reason = error.string("error_msg") ?: "VK error",
                captchaSid = error.string("captcha_sid"),
                captchaImageUrl = error.string("captcha_img")
            )
        }

        return root["response"]
            ?: throw VkApiException(-1, "VK returned no response for $method")
    }

    suspend fun callObject(
        method: String,
        params: Map<String, String> = emptyMap()
    ): JsonObject = call(method, params).jsonObject

    suspend fun callArray(
        method: String,
        params: Map<String, String> = emptyMap()
    ): List<JsonObject> = (call(method, params) as? JsonArray)?.objects() ?: emptyList()

    private suspend fun request(method: String, params: Map<String, String>): String? {
        val body = encodeForm(
            params + mapOf(
                "access_token" to token,
                "v" to VkApiVersion
            )
        ).encodeToByteArray()

        return vkHttpRequest(
            url = "$VkApiUrl/$method",
            method = "POST",
            contentType = "application/x-www-form-urlencoded",
            body = body
        )
    }

    /**
     * Uploads bytes to one of VK's upload servers. Photos use the `photo`
     * field, documents the `file` field; both answer with JSON that has to be
     * passed to the matching `save*` method.
     */
    suspend fun upload(
        uploadUrl: String,
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String
    ): JsonObject? {
        val response = vkHttpRequest(
            url = uploadUrl,
            method = "POST",
            contentType = "multipart/form-data; boundary=${MultipartBoundary}",
            body = buildMultipart(fieldName, fileName, bytes, contentType),
            timeoutSeconds = 120
        ) ?: return null

        return runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
    }

    private fun buildMultipart(
        fieldName: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String
    ): ByteArray {
        val boundary = MultipartBoundary
        val head = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n" +
                "Content-Type: $contentType\r\n\r\n"
            ).encodeToByteArray()
        val tail = "\r\n--$boundary--\r\n".encodeToByteArray()

        return head + bytes + tail
    }

    private companion object {
        val MultipartBoundary: String =
            "$VkUploadMultipartBoundaryPrefix${Random.nextLong().toULong().toString(16)}"
    }
}

/** One page of Long Poll events, with the `ts` to resume from. */
internal data class VkLongPollPage(
    val ts: String?,
    val failed: Int?,
    val updates: List<JsonArray>
)

/**
 * Parses a Long Poll `a_check` answer.
 *
 * A `failed` value of 1 means "resume with a fresh key", 2 "get a new key",
 * 3 "the key expired" and 4 "the version is outdated". The caller drops the
 * current server/key in every case and asks for new ones.
 */
internal fun parseLongPollPage(body: String, json: Json): VkLongPollPage? {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return null

    val failed = root["failed"].asLongOrNull()?.toInt()
    val ts = root["ts"].asStringOrNull()
    val updates = (root["updates"] as? JsonArray)?.mapNotNull { element ->
        element as? JsonArray
    }.orEmpty()

    return VkLongPollPage(ts = ts, failed = failed, updates = updates)
}

/** Builds the `a_check` URL from the values `messages.getLongPollServer` returns. */
internal fun longPollUrl(
    server: String,
    key: String,
    ts: String,
    waitSeconds: Int = 25
): String {
    val host = if (server.startsWith("http")) server else "https://$server"
    return "$host?act=a_check&key=${percentEncode(key)}&ts=${percentEncode(ts)}" +
        "&wait=$waitSeconds&mode=2&version=3"
}

/** Stable synthetic media id, so UI code can refer to a URL by int file id. */
internal fun mediaIdForUrl(url: String): Int {
    val hash = url.hashCode()
    return if (hash == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(hash)
}
