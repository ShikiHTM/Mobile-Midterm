package com.nhom6.imagegallery

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NaiveImageLoader: ImageLoaderStrategy {

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun loadImage(url: String, imageView: ImageView, targetWidth: Int, targetHeight: Int) {
        imageView.tag = url

        executor.execute {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection

                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.connect()

                val inputStream = connection.getInputStream()

                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                mainHandler.post {
                    if (imageView.tag == url && bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }catch(e: Exception) {
                e.printStackTrace()
            }
        }
    }
}