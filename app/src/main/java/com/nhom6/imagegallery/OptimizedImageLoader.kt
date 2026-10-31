package com.nhom6.imagegallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

class OptimizedImageLoader(private val context: Context): ImageLoaderStrategy {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = ConcurrentHashMap<ImageView, Job>()

    private val totalRequests = AtomicInteger(0)
    private val memoryHits = AtomicInteger(0)
    private val diskHits = AtomicInteger(0)
    private val networkRequests = AtomicInteger(0)

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
                    val cacheDir = File(context.cacheDir, "images")
                    diskCache = DiskLruCache.open(
                        cacheDir,
                        DISK_APP_VERSION,
                        DISK_CACHE_VALUE_COUNT,
                        MAX_CACHE_SIZE
                    )
                } catch(e: IOException) {
                    Log.e(TAG, "Can not open disk cache.", e)
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
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    override fun cancelRequest(imageView: ImageView) {
        runningJobs.remove(imageView)?.let { job ->
            job.cancel()
            Log.d(TAG, "Cancelled image request for target ImageView")
        }
    }

    override fun cancelAllRequests() {
        val count = runningJobs.size
        runningJobs.values.forEach { it.cancel() }
        runningJobs.clear()
        Log.d(TAG, "Cancelled all $count pending image requests.")
    }

    override fun loadImage(
        url: String,
        imageView: ImageView,
        targetWidth: Int,
        targetHeight: Int,
        @DrawableRes placeholderRes: Int?,
        @DrawableRes errorRes: Int?
    ) {
        cancelRequest(imageView)

        val startTime = System.currentTimeMillis()
        totalRequests.incrementAndGet()

        imageView.tag = url
        val safeKey = hashKeyForDisk(url)

        val bitmapFormMem = memoryCache.get(safeKey)
        if (bitmapFormMem != null) {
            memoryHits.incrementAndGet()
            logBenchmark("MEMORY_CACHE", startTime, url)
            imageView.setImageBitmap(bitmapFormMem)
            return
        }

        if (placeholderRes != null) {
            imageView.setImageResource(placeholderRes)
        } else {
            imageView.setImageDrawable(null)
        }

        val job = scope.launch {
            val cache = getDiskCache()
            var snapshot = cache?.get(safeKey)
            var loadedFromNetwork = false

            if (snapshot == null) {
                if (!isActive) return@launch
                loadedFromNetwork = true
                networkRequests.incrementAndGet()
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
                            if (isActive) {
                                editor.commit()
                            } else {
                                editor.abort()
                                return@launch
                            }
                        } catch (e: IOException) {
                            editor.abort()
                            throw e
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        Log.e(TAG, "Can not load image from network: $url", e)
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == url && errorRes != null) {
                                imageView.setImageResource(errorRes)
                            }
                        }
                    }
                    return@launch
                }

                if (!isActive) return@launch
                snapshot = cache?.get(safeKey)
            } else {
                diskHits.incrementAndGet()
            }

            if (!isActive) {
                snapshot?.close()
                return@launch
            }

            if (snapshot != null) {
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
                        val source = if (loadedFromNetwork) "NETWORK" else "DISK_CACHE"
                        logBenchmark(source, startTime, url)

                        withContext(Dispatchers.Main) {
                            if (imageView.tag == url) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == url && errorRes != null) {
                                imageView.setImageResource(errorRes)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Can not load image from disk cache for key: $safeKey", e)
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == url && errorRes != null) {
                                imageView.setImageResource(errorRes)
                            }
                        }
                    }
                } finally {
                    snapshot.close()
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (imageView.tag == url && errorRes != null) {
                        imageView.setImageResource(errorRes)
                    }
                }
            }
        }

        runningJobs[imageView] = job
        job.invokeOnCompletion {
            runningJobs.remove(imageView, job)
        }
    }

    private fun logBenchmark(source: String, startTime: Long, url: String) {
        val duration = System.currentTimeMillis() - startTime
        val total = totalRequests.get()
        val memHits = memoryHits.get()
        val dHits = diskHits.get()
        val netReqs = networkRequests.get()
        val totalCacheHits = memHits + dHits
        val hitRate = if (total > 0) (totalCacheHits * 100.0 / total) else 0.0

        val tagPrefix = when (source) {
            "MEMORY_CACHE" -> "[MEMORY_CACHE]"
            "DISK_CACHE" -> "[DISK_CACHE]"
            else -> "[NETWORK]"
        }

        val formattedHitRate = String.format(Locale.US, "%.1f", hitRate)
        Log.d(
            TAG,
            "$tagPrefix Loaded in ${duration}ms | Cache Hit Rate: $formattedHitRate% (Mem: $memHits, Disk: $dHits, Net: $netReqs, Total: $total) | URL: $url"
        )
    }

    override fun logBenchmarkSummary() {
        val total = totalRequests.get()
        if (total == 0) {
            Log.d(TAG, "Benchmark Summary: No image requests recorded yet.")
            return
        }
        val memHits = memoryHits.get()
        val dHits = diskHits.get()
        val netReqs = networkRequests.get()
        val overallHits = memHits + dHits
        val hitRate = overallHits * 100.0 / total
        val memHitRate = memHits * 100.0 / total
        val diskHitRate = dHits * 100.0 / total

        val summary = """
            ====================================================
            OPTIMIZED IMAGE LOADER BENCHMARK SUMMARY
            ----------------------------------------------------
            Total Requests    : $total
            Memory Cache Hits : $memHits (${String.format(Locale.US, "%.1f", memHitRate)}%)
            Disk Cache Hits   : $dHits (${String.format(Locale.US, "%.1f", diskHitRate)}%)
            Network Downloads : $netReqs (${String.format(Locale.US, "%.1f", netReqs * 100.0 / total)}%)
            OVERALL HIT RATE  : ${String.format(Locale.US, "%.1f", hitRate)}%
            ====================================================
        """.trimIndent()
        Log.d(TAG, summary)
    }

    override fun resetBenchmark() {
        totalRequests.set(0)
        memoryHits.set(0)
        diskHits.set(0)
        networkRequests.set(0)
        Log.d(TAG, "Benchmark metrics reset.")
    }

    override fun clearCache() {
        cancelAllRequests()
        memoryCache.evictAll()
        try {
            diskCache?.delete()
            diskCache = null
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing disk cache.", e)
        }
        resetBenchmark()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    companion object {
        private const val TAG = "ImageLoaderBenchmark"
        const val MAX_CACHE_SIZE: Long = 100 * 1024 * 1024 // 100 MB in byte
        const val DISK_APP_VERSION = 1
        const val DISK_CACHE_VALUE_COUNT = 1
    }
}
