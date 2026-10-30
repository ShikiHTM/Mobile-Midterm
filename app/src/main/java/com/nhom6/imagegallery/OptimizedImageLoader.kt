package com.nhom6.imagegallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.Volatile

class OptimizedImageLoader(private val context: Context): ImageLoaderStrategy {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var diskCache: DiskLruCache? = null

    private val memoryCache by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String?, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    private fun getDiskCache(): DiskLruCache? {

        if (diskCache != null) return diskCache

        synchronized(this) {
            if (diskCache == null) {
                try {
                    val cacheDir = File(context.cacheDir, "images");
                    diskCache = DiskLruCache.open(
                        cacheDir,
                        DISK_APP_VERSION,
                        DISK_CACHE_VALUE_COUNT,
                        MAX_CACHE_SIZE
                    )
                } catch(e: IOException) {
                    Log.e("ImageLoader", "Can not open disk cache.")
                    e.printStackTrace()
                }
            }
        }
        return diskCache
    }

    private fun hashKeyForDisk(key: String): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(key.toByteArray())
        val bytes = messageDigest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    override fun loadImage(url: String, imageView: ImageView, targetWidth: Int, targetHeight: Int) {
        imageView.tag = url
        val safeKey = hashKeyForDisk(url)

        val bitmapFormMem = memoryCache.get(safeKey)
        if (bitmapFormMem != null) {
            imageView.setImageBitmap(bitmapFormMem)
            return
        }

        scope.launch {
            val cache = getDiskCache()

            var snapshot = cache?.get(safeKey)

            if (snapshot == null) {
                try {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()

                    val inputStream = connection.getInputStream()

                    val editor = cache?.edit(safeKey)
                    if (editor != null) {
                        try {
                            editor.newOutputStream(0).use {
                                inputStream.copyTo(it)
                            }
                            editor.commit()
                        } catch (e: IOException) {
                            editor.abort()
                            throw e
                        }
                    }
                } catch (e: IOException) {
                    Log.e("ImageLoader", "Can not load image from network.", e)
                    return@launch
                }

                snapshot = cache?.get(safeKey)
            }

            if(snapshot != null) {
                try {
                    val fileInputStream = snapshot.getInputStream(0) as java.io.FileInputStream
                    val fileDescriptor = fileInputStream.fd
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }

                    BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)

                    options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                    options.inJustDecodeBounds = false

                    val bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)

                    if (bitmap != null) {
                        memoryCache.put(safeKey, bitmap)
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImageLoader", "Can not load image from disk cache.")
                    e.printStackTrace()
                } finally {
                    snapshot.close()
                }
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth}
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    companion object {
        const val MAX_CACHE_SIZE: Long = 100 * 1024 * 1024 // 100 MB in byte
        const val DISK_APP_VERSION = 1
        const val DISK_CACHE_VALUE_COUNT = 1
    }
}
