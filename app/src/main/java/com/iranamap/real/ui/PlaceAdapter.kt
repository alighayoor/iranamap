package com.iranamap.real.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.R
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.PersianText

/** ردیف یکسان برای نتیجه‌ی جست‌وجوی متنی و دسته‌بندی‌شده. */
data class PlaceRow(
    val title: String,
    val subtitle: String,
    val point: GeoPoint,
    val distanceMeters: Double? = null,
    val glyph: String = "📍",
    val address: String = ""
)

class PlaceAdapter(
    private val onClick: (PlaceRow) -> Unit
) : RecyclerView.Adapter<PlaceAdapter.Holder>() {

    private val items = ArrayList<PlaceRow>()

    fun submit(rows: List<PlaceRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun size(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_poi, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val glyph: TextView = itemView.findViewById(R.id.tvGlyph)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val distance: TextView = itemView.findViewById(R.id.tvDistance)

        fun bind(row: PlaceRow) {
            glyph.text = row.glyph
            title.text = row.title
            subtitle.text = row.subtitle.ifBlank { row.address }
            val d = row.distanceMeters
            if (d != null && d > 0) {
                distance.text = PersianText.formatDistance(d)
                distance.visibility = View.VISIBLE
            } else {
                distance.visibility = View.GONE
            }
            itemView.setOnClickListener { onClick(row) }
        }
    }
}
