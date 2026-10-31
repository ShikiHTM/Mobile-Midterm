package com.nhom6.imagegallery

import android.widget.ImageView
import androidx.annotation.DrawableRes

interface ImageLoaderStrategy {
    fun loadImage(
        url: String,
        imageView: ImageView,
        targetWidth: Int,
        targetHeight: Int,
        @DrawableRes placeholderRes: Int? = R.drawable.ic_placeholder_image,
        @DrawableRes errorRes: Int? = R.drawable.ic_error_image
    )
    fun cancelRequest(imageView: ImageView) {}
    fun cancelAllRequests() {}
    fun clearCache() {}
    fun logBenchmarkSummary() {}
    fun resetBenchmark() {}
}
