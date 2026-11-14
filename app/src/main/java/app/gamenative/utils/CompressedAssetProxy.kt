package app.gamenative.utils

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import java.io.Closeable
import java.io.File
import java.io.IOException

/** 実験用: 単一圧縮ファイルを Android の読み取り専用シーク可能 FD として公開する。
 * ファイルパスとしての公開や Wine の実ゲームとの接続ではない。 */
class CompressedAssetProxy private constructor(
    val descriptor: ParcelFileDescriptor,
) : Closeable {
    override fun close() = descriptor.close()

    companion object {
        fun open(context: Context, packed: File): CompressedAssetProxy {
            val reader = ChunkedAssetFile.open(packed)
            val thread = HandlerThread("compressed-asset-proxy").apply { start() }
            try {
                val manager = context.getSystemService(StorageManager::class.java)
                    ?: throw IOException("ストレージサービスが利用できません")
                val callback = object : ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long = reader.originalSize

                    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                        if (offset < 0 || size < 0 || size > data.size) throw ErrnoException("read", OsConstants.EINVAL)
                        if (offset >= reader.originalSize || size == 0) return 0
                        val count = minOf(size.toLong(), reader.originalSize - offset, ChunkedAssetFile.MAX_READ.toLong()).toInt()
                        try {
                            reader.read(offset, count).copyInto(data, 0)
                        } catch (_: Exception) { throw ErrnoException("read", OsConstants.EIO) }
                        return count
                    }

                    override fun onRelease() {
                        try { reader.close() } finally { thread.quitSafely() }
                    }
                }
                return CompressedAssetProxy(manager.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY, callback, Handler(thread.looper),
                ))
            } catch (e: Exception) {
                reader.close()
                thread.quitSafely()
                throw e
            }
        }
    }
}
