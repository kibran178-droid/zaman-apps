package com.example.zaman_apps.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.zaman_apps.R

class GalleryAdapter(
    private var items: List<String>,
    private val onZoomStateChanged: (Boolean) -> Unit,
    private val onSingleTap: (() -> Unit)? = null
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    fun updateItems(newItems: List<String>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_photo, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(items[position], onZoomStateChanged, onSingleTap)
    }

    override fun getItemCount(): Int = items.size

    class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: ZoomableImageView = itemView.findViewById(R.id.ivZoomablePhoto)
        private val pbLoading: ProgressBar = itemView.findViewById(R.id.pbPhotoLoading)

        fun bind(itemStr: String, onZoomChanged: (Boolean) -> Unit, onSingleTap: (() -> Unit)?) {
            ivPhoto.resetZoom()
            ivPhoto.onZoomStateChanged = onZoomChanged
            ivPhoto.onSingleTapListener = onSingleTap

            pbLoading.visibility = View.VISIBLE

            if (itemStr.startsWith("http")) {
                val fileIdMatch = Regex("/d/([a-zA-Z0-9_-]+)").find(itemStr)
                val directUrl = if (fileIdMatch != null) {
                    "https://drive.google.com/uc?export=view&id=${fileIdMatch.groupValues[1]}"
                } else {
                    itemStr
                }
                Glide.with(itemView.context)
                    .load(directUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            pbLoading.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            pbLoading.visibility = View.GONE
                            return false
                        }
                    })
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(ivPhoto)
            } else {
                pbLoading.visibility = View.GONE
                try {
                    val uri = Uri.parse(itemStr)
                    val bitmap: Bitmap? = ImageHelper.decodeThumbnailFromUri(itemView.context, uri, 1280, 1280)
                    if (bitmap != null) {
                        ivPhoto.setImageBitmap(bitmap)
                    } else {
                        ivPhoto.setImageURI(uri)
                    }
                } catch (_: Exception) {
                    ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
    }
}
