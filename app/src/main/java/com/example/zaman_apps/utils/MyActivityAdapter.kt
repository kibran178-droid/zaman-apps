package com.example.zaman_apps.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.zaman_apps.R
import org.json.JSONObject

class MyActivityAdapter(
    private var items: List<JSONObject>
) : RecyclerView.Adapter<MyActivityAdapter.MyActivityViewHolder>() {

    fun updateData(newItems: List<JSONObject>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_activity_log, parent, false)
        return MyActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyActivityViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class MyActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadge: TextView = itemView.findViewById(R.id.tvActionBadge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSiteTitle)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvActionTimestamp)

        fun bind(obj: JSONObject) {
            val actionType = obj.optString("action_type").uppercase()
            val isInput = actionType.contains("INPUT")

            if (isInput) {
                tvBadge.text = "INPUT"
                tvBadge.setBackgroundResource(R.drawable.bg_stat_item)
                tvBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_primary))
            } else {
                tvBadge.text = "UPDATE"
                tvBadge.setBackgroundResource(R.drawable.bg_stat_item_update)
                tvBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning))
            }

            val siteId = obj.optString("site_id").ifEmpty { obj.optString("id_site") }
            val siteName = obj.optString("site_name").ifEmpty { obj.optString("nama_site") }

            val titleText = when {
                siteId.isNotEmpty() && siteName.isNotEmpty() -> "$siteId - $siteName"
                siteId.isNotEmpty() -> siteId
                siteName.isNotEmpty() -> siteName
                else -> "Aktivitas Data Site"
            }
            tvTitle.text = titleText

            val rawDate = obj.optString("timestamp").ifEmpty { obj.optString("tanggal") }
            tvTimestamp.text = if (rawDate.isNotEmpty()) rawDate else "-"
        }
    }
}
