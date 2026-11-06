package com.prism.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun send(request: Request, client: OkHttpClient = this.client): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpException(r.code, "${r.request.url.host}${r.request.url.encodedPath}", body)
            body
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        send(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        json.parseToJsonElement(get(url, headers + ("Accept" to "application/json")))
}

/** Non-2xx response; keeps the server's own message so it can be shown to the user. */
class HttpException(val code: Int, val where: String, val body: String) :
    IOException("HTTP $code from $where" + body.trim().take(200).let { if (it.isBlank() || it.startsWith("<")) "" else ": $it" })

// Small helpers for walking loosely-typed JSON from servers.
fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.i(key: String): Int? = s(key)?.toDoubleOrNull()?.toInt()
fun JsonObject.l(key: String): Long? = s(key)?.toDoubleOrNull()?.toLong()
fun JsonObject.d(key: String): Double? = s(key)?.toDoubleOrNull()
fun JsonObject.b(key: String): Boolean? = s(key)?.toBooleanStrictOrNull()
fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
fun JsonElement.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
