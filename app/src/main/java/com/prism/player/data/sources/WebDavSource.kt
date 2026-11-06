package com.prism.player.data.sources

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.prism.player.data.Net
import com.prism.player.data.SourceConfig
import com.prism.player.data.Tmdb
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale

class WebDavSource(config: SourceConfig, context: Context, tmdb: Tmdb) : FileSource(config, context, tmdb) {
    private val base = config.url.trimEnd('/') + "/"

    override val streamHeaders: Map<String, String> =
        if (config.username.isNotBlank()) mapOf("Authorization" to Credentials.basic(config.username, config.password))
        else emptyMap()

    override fun streamUri(path: String) = base + path.split('/').joinToString("/") { Uri.encode(it) }

    override suspend fun list(path: String): List<RemoteFile> {
        val url = streamUri(path)
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/></d:prop></d:propfind>"""
        val request = Request.Builder().url(url)
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .apply { streamHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        val xml = Net.send(request)
        val selfPath = Uri.decode(url.toHttpUrlOrNull()?.encodedPath.orEmpty()).trimEnd('/')
        return parse(xml).mapNotNull { r ->
            val hrefPath = Uri.decode(
                if (r.href.startsWith("http")) r.href.toHttpUrlOrNull()?.encodedPath.orEmpty() else r.href
            ).trimEnd('/')
            if (hrefPath == selfPath || hrefPath.isEmpty()) return@mapNotNull null
            val name = hrefPath.substringAfterLast('/')
            RemoteFile(name, path + name + if (r.isDir) "/" else "", r.isDir, r.modified)
        }
    }

    private class Entry(val href: String, val isDir: Boolean, val modified: Long)

    private fun parse(xml: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())
        var href: String? = null
        var isDir = false
        var modified = 0L
        var text = StringBuilder()
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name) {
                        "response" -> { href = null; isDir = false; modified = 0 }
                        "collection" -> isDir = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "href" -> href = text.toString().trim()
                    "getlastmodified" -> modified = runCatching { dateFormat.parse(text.toString().trim())?.time ?: 0 }.getOrDefault(0)
                    "response" -> href?.let { out += Entry(it, isDir, modified) }
                }
            }
        }
        return out
    }
}
