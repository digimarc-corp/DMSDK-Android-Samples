package com.digimarc.dvdemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.digimarc.dms.resolver.ContentItem
import com.digimarc.dms.resolver.ResolvedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.net.HttpURLConnection
import java.net.URL

const val MAX_ITEMS = 15 // Cap entries so that we don't saturate device memory with bitmaps

class DISAdapter(val scope: CoroutineScope, private val clickListener: DISItemClickListener) :
        RecyclerView.Adapter<DISAdapter.ViewHolder>() {
    companion object {
        private val thumbnailCache = mutableMapOf<String, Bitmap>()
        private val options: BitmapFactory.Options = BitmapFactory.Options().apply {
            outWidth = 128
            outHeight = 128
        }
    }

    var items = mutableListOf<DISItem>()

    fun add(item: DISItem) {
        scope.launch {
            updateData(item)
            notifyDataSetChanged()
        }
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    private suspend fun updateData(item: DISItem) =
            withContext(Dispatchers.Default) {
                // Remove entries with the same payload
                items.remove(item)
                if (items.size >= MAX_ITEMS) {
                    thumbnailCache.remove(items[0].content.contentItems[0].thumbnailUrl)
                    items.remove(items[0])
                }
                items.add(item)
            }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val titleView = view.findViewById(R.id.title) as TextView
        val subtitleView = view.findViewById(R.id.subtitle) as TextView
        val thumbnailView = view.findViewById(R.id.thumbnail) as ImageView

        var thumbailUrl: String = ""
            set(value) {
                scope.launch {
                    when {
                        value.isEmpty() -> thumbnailView.setImageBitmap(null)
                        thumbnailCache.containsKey(value) -> thumbnailView.setImageBitmap(thumbnailCache[value])
                        else -> {
                            fetchServiceImage(value)?.let { bmp ->
                                thumbnailCache[value] = bmp
                                thumbnailView.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            }

        private suspend fun fetchServiceImage(path: String) =
                withContext(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(path)
                        connection = (url.openConnection() as? HttpURLConnection)
                        connection?.run {
                            requestMethod = "GET"

                            connect()

                            inputStream?.let {
                                BitmapFactory.decodeStream(it, null, options)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    } finally {
                        connection?.inputStream?.close()
                        connection?.disconnect()
                    }
                }
    }

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater
                .inflate(R.layout.item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Display the first content item
        val content = item.content.contentItems[0]
        content?.let {
            holder.titleView.text = it.title
            if (it.subTitle.isNotEmpty()) {
                holder.subtitleView.text = it.subTitle
                holder.subtitleView.visibility = View.VISIBLE
            } else {
                holder.subtitleView.visibility = View.GONE
            }

            // If we already have an image, load it into the view. Otherwise, set the
            // thumbnail url, which will trigger download of the service image tied
            // to this content.
            if (item.img != null) {
                holder.thumbnailView.setImageBitmap(item.img)
            } else {
                holder.thumbnailView.setImageBitmap(null)
                holder.thumbailUrl = it.thumbnailUrl
            }

            holder.view.setOnClickListener {
                clickListener.onClick(content)
            }
        }
    }

    override fun getItemCount() = items.size
}

class DISItemClickListener(val clickListener: (url: String) -> Unit) {
    fun onClick(contentItem: ContentItem) = clickListener(contentItem.content)
}

@Parcelize
@TypeParceler<ResolvedContent, ResolvedContentParceler>()
data class DISItem(val content: ResolvedContent, val img: Bitmap?) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DISItem

        return content.payload.payloadString == other.content.payload.payloadString
    }

    override fun hashCode() = content.hashCode()
}

object ResolvedContentParceler : Parceler<ResolvedContent> {
    override fun create(parcel: Parcel) = ResolvedContent(parcel.readString()!!)
    override fun ResolvedContent.write(parcel: Parcel, flags: Int) = parcel.writeString(rawJson)
}