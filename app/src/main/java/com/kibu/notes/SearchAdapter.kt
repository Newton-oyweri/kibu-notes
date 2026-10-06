package com.kibu.notes

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Resource
import java.util.Locale

sealed class SearchResultItem {
    data class Header(val title: String) : SearchResultItem()

    data class AcademicUnitItem(
        val unit: AcademicUnit,
        val subtitle: String
    ) : SearchResultItem()

    data class ResourceItem(
        val resource: Resource,
        val unitCode: String?,
        val subtitle: String
    ) : SearchResultItem()

    data class LocalFileItem(
        val name: String,
        val uri: Uri,
        val relativePath: String,
        val isDirectory: Boolean,
        val mimeType: String?
    ) : SearchResultItem()

    data class RecentItem(
        val name: String,
        val uri: Uri,
        val subtitle: String
    ) : SearchResultItem()
}

class SearchAdapter(
    private val onItemClick: (SearchResultItem) -> Unit
) : ListAdapter<SearchResultItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RESULT = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean {
                return when {
                    oldItem is SearchResultItem.Header && newItem is SearchResultItem.Header ->
                        oldItem.title == newItem.title
                    oldItem is SearchResultItem.AcademicUnitItem && newItem is SearchResultItem.AcademicUnitItem ->
                        oldItem.unit.id == newItem.unit.id
                    oldItem is SearchResultItem.ResourceItem && newItem is SearchResultItem.ResourceItem ->
                        oldItem.resource.id == newItem.resource.id
                    oldItem is SearchResultItem.LocalFileItem && newItem is SearchResultItem.LocalFileItem ->
                        oldItem.uri == newItem.uri
                    oldItem is SearchResultItem.RecentItem && newItem is SearchResultItem.RecentItem ->
                        oldItem.uri == newItem.uri
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchResultItem.Header -> TYPE_HEADER
            else -> TYPE_RESULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_search_header, parent, false))
        } else {
            ResultViewHolder(inflater.inflate(R.layout.item_search_result, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is SearchResultItem.Header) {
            holder.bind(item)
        } else if (holder is ResultViewHolder) {
            holder.bind(item, onItemClick)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)

        fun bind(item: SearchResultItem.Header) {
            tvTitle.text = item.title
        }
    }

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivResultIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvResultTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvResultSubtitle)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvResultBadge)

        fun bind(item: SearchResultItem, onClick: (SearchResultItem) -> Unit) {
            itemView.setOnClickListener { onClick(item) }

            when (item) {
                is SearchResultItem.AcademicUnitItem -> {
                    tvTitle.text = item.unit.title
                    tvSubtitle.text = item.subtitle
                    tvBadge.text = item.unit.code.ifEmpty { "UNIT" }
                    ivIcon.setImageResource(R.drawable.ic_search)
                }
                is SearchResultItem.ResourceItem -> {
                    tvTitle.text = item.resource.title.ifEmpty { item.resource.fileName.ifEmpty { "Study Material" } }
                    val codePrefix = if (!item.unitCode.isNullOrEmpty()) "${item.unitCode} • " else ""
                    tvSubtitle.text = "$codePrefix${item.subtitle}"
                    val badgeType = item.resource.resourceType.ifEmpty { getExtension(item.resource.fileName) }
                    tvBadge.text = badgeType.uppercase(Locale.ROOT)
                    ivIcon.setImageResource(R.drawable.ic_search)
                }
                is SearchResultItem.LocalFileItem -> {
                    tvTitle.text = item.name
                    tvSubtitle.text = item.relativePath
                    tvBadge.text = if (item.isDirectory) "FOLDER" else getExtension(item.name)
                    ivIcon.setImageResource(R.drawable.ic_search)
                }
                is SearchResultItem.RecentItem -> {
                    tvTitle.text = item.name
                    tvSubtitle.text = item.subtitle
                    tvBadge.text = "RECENT"
                    ivIcon.setImageResource(R.drawable.ic_search)
                }
                else -> {}
            }
        }

        private fun getExtension(fileName: String): String {
            val idx = fileName.lastIndexOf('.')
            return if (idx in 1 until fileName.length - 1) {
                fileName.substring(idx + 1).uppercase(Locale.ROOT)
            } else {
                "FILE"
            }
        }
    }
}
