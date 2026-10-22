package com.iranamap.real.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.R
import com.iranamap.real.core.PersianText
import com.iranamap.real.nav.ActiveRoute

/** انتخاب بین مسیر اصلی و مسیرهای جایگزین. */
class RouteAdapter(
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<RouteAdapter.Holder>() {

    private val routes = ArrayList<ActiveRoute>()

    fun submit(list: List<ActiveRoute>) {
        routes.clear()
        routes.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(routes[position], position)

    override fun getItemCount(): Int = routes.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val color: View = itemView.findViewById(R.id.vColor)
        private val title: TextView = itemView.findViewById(R.id.tvRouteTitle)
        private val detail: TextView = itemView.findViewById(R.id.tvRouteDetail)
        private val badge: TextView = itemView.findViewById(R.id.tvRouteBadge)

        fun bind(route: ActiveRoute, index: Int) {
            val ctx = itemView.context
            title.text = if (index == 0) {
                ctx.getString(R.string.route_fastest)
            } else {
                ctx.getString(R.string.routes) + " " + PersianText.toPersianDigits((index + 1).toString())
            }
            detail.text = PersianText.formatDistance(route.totalMeters) + " • " +
                PersianText.formatDuration(route.totalSeconds.toLong())
            badge.text = PersianText.clockString(System.currentTimeMillis() + route.totalSeconds.toLong() * 1000L)
            color.setBackgroundColor(
                ContextCompat.getColor(ctx, if (index == 0) R.color.route_primary else R.color.route_alt)
            )
            itemView.setOnClickListener { onPick(index) }
        }
    }
}
