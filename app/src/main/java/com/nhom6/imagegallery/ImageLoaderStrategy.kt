package com.nhom6.imagegallery

import android.widget.ImageView

interface ImageLoaderStrategy {
    fun loadImage(url: String, imageView: ImageView, targetWidth: Int, targetHeight: Int)
    fun clearCache() {}
}