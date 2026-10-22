package com.iranamap.real.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.R
import com.iranamap.real.core.GeoPoint

/** یک ردیف مشترک برای ذخیره‌ها و تاریخچه. */
data class FavoriteRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val point: GeoPoint,
    val icon: String = "⭐",
    val deletable: Boolean = true
)

class FavoriteAdapter(
    private val onNavigate: (FavoriteRow) -> Unit,
    private val onDelete: (FavoriteRow) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.Holder>() {

    private val items = ArrayList<FavoriteRow>()

    fun submit(rows: List<FavoriteRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: TextView = itemView.findViewById(R.id.tvIcon)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val btnNavigate: ImageButton = itemView.findViewById(R.id.btnNavigate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(row: FavoriteRow) {
            icon.text = row.icon
            title.text = row.title
            subtitle.text = row.subtitle
            btnNavigate.setOnClickListener { onNavigate(row) }
            btnDelete.visibility = if (row.deletable) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(row) }
            itemView.setOnClickListener { onNavigate(row) }
        }
    }
}
