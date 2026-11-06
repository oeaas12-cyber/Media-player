package com.prism.player.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import jcifs.CIFSContext
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import kotlin.math.min

/** Streams smb:// URIs straight from a share, with seeking and read-ahead buffering. */
@UnstableApi
class SmbDataSource(private val cifs: CIFSContext) : BaseDataSource(/* isNetwork = */ true) {
    private var file: SmbFile? = null
    private var raf: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    private val buffer = ByteArray(BUFFER_SIZE)
    private var bufPos = 0
    private var bufLen = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val f = SmbFile(dataSpec.uri.toString(), cifs)
            file = f
            val r = SmbRandomAccessFile(f, "r")
            raf = r
            val length = r.length()
            if (dataSpec.position > length) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            r.seek(dataSpec.position)
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else length - dataSpec.position
        } catch (e: DataSourceException) {
            throw e
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        bufPos = 0
        bufLen = 0
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (bufPos >= bufLen) {
            val want = min(BUFFER_SIZE.toLong(), bytesRemaining).toInt()
            val n = try {
                raf!!.read(buffer, 0, want)
            } catch (e: IOException) {
                throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
            }
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufPos = 0
            bufLen = n
        }
        val count = min(length, bufLen - bufPos)
        System.arraycopy(buffer, bufPos, target, offset, count)
        bufPos += count
        bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            raf?.close()
            file?.close()
        } catch (_: Exception) {
        } finally {
            raf = null
            file = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val cifs: CIFSContext) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(cifs)
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20 // 1 MiB read-ahead per request
    }
}
