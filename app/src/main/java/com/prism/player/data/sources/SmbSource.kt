package com.prism.player.data.sources

import android.content.Context
import com.prism.player.data.SourceConfig
import com.prism.player.data.Tmdb
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

object SmbContexts {
    private val base: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
        }
        BaseContext(PropertyConfiguration(props))
    }

    fun forConfig(c: SourceConfig): CIFSContext =
        if (c.username.isBlank()) base.withAnonymousCredentials()
        else base.withCredentials(NtlmPasswordAuthenticator(c.domain.ifBlank { null }, c.username, c.password))

    /** Normalises user input like "nas.local", "Movies" into smb://nas.local/Movies/ */
    fun buildUrl(host: String, path: String): String {
        val h = host.trim().removePrefix("smb://").trim('/')
        val p = path.trim().replace('\\', '/').trim('/')
        return if (p.isEmpty()) "smb://$h/" else "smb://$h/$p/"
    }
}

class SmbSource(config: SourceConfig, context: Context, tmdb: Tmdb) : FileSource(config, context, tmdb) {
    private val cifs by lazy { SmbContexts.forConfig(config) }
    private val root = config.url.trimEnd('/') + "/"

    override val smbConfig: SourceConfig get() = config

    override fun streamUri(path: String) = root + path

    override suspend fun list(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        SmbFile(root + path, cifs).use { dir ->
            dir.listFiles().map { f ->
                f.use {
                    val isDir = f.isDirectory
                    val name = f.name.trimEnd('/')
                    RemoteFile(name, path + name + if (isDir) "/" else "", isDir, runCatching { f.lastModified() }.getOrDefault(0))
                }
            }
        }
    }
}
