package com.nhom6.imagegallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PhotoAdapter(
    private var imageUrls: List<String>,
    var imageLoader: ImageLoaderStrategy,
    private val onItemClick: (String) -> Unit
): RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.iv_thumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val url = imageUrls[position]

        imageLoader.loadImage(url, holder.imageView, 200, 200)

        holder.imageView.setOnClickListener {
            onItemClick(url)
        }
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        super.onViewRecycled(holder)
        imageLoader.cancelRequest(holder.imageView)
    }

    override fun getItemCount(): Int {
        return imageUrls.size
    }

    fun updateData(newUrls: List<String>) {
        this.imageUrls = newUrls
        notifyDataSetChanged()
    }
}
