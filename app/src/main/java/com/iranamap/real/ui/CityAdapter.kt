package com.iranamap.real.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.iranamap.real.R
import com.iranamap.real.core.City
import com.iranamap.real.core.OfflinePlanner
import com.iranamap.real.core.PersianText

data class CityRow(
    val city: City,
    val downloadedSizeBytes: Long = 0L,
    val inProgress: Boolean = false,
    val progress: Int = 0
) {
    val isDownloaded: Boolean get() = downloadedSizeBytes > 0L
}

class CityAdapter(
    private val onDownload: (City) -> Unit,
    private val onDelete: (City) -> Unit
) : RecyclerView.Adapter<CityAdapter.Holder>() {

    private val items = ArrayList<CityRow>()

    fun submit(rows: List<CityRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    fun updateProgress(cityId: String, progress: Int) {
        val index = items.indexOfFirst { it.city.id == cityId }
        if (index >= 0) {
            items[index] = items[index].copy(inProgress = true, progress = progress)
            notifyItemChanged(index)
        }
    }

    fun markDownloaded(cityId: String, sizeBytes: Long) {
        val index = items.indexOfFirst { it.city.id == cityId }
        if (index >= 0) {
            items[index] = items[index].copy(downloadedSizeBytes = sizeBytes, inProgress = false, progress = 100)
            notifyItemChanged(index)
        }
    }

    fun markDeleted(cityId: String) {
        val index = items.indexOfFirst { it.city.id == cityId }
        if (index >= 0) {
            items[index] = items[index].copy(downloadedSizeBytes = 0L, inProgress = false, progress = 0)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_city, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvCityName)
        private val info: TextView = itemView.findViewById(R.id.tvCityInfo)
        private val action: MaterialButton = itemView.findViewById(R.id.btnCityAction)
        private val delete: ImageButton = itemView.findViewById(R.id.btnCityDelete)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressCity)

        fun bind(row: CityRow) {
            val ctx = itemView.context
            name.text = row.city.name
            val plan = OfflinePlanner.planForCity(row.city)
            val estimate = PersianText.fileSize(plan.totalBytes)
            info.text = when {
                row.inProgress -> ctx.getString(R.string.download_progress, row.progress, "", estimate)
                row.isDownloaded -> ctx.getString(R.string.downloaded_city) + " • " +
                    PersianText.fileSize(row.downloadedSizeBytes)
                else -> row.city.province + " • " + ctx.getString(R.string.download_size, estimate)
            }
            progressBar.visibility = if (row.inProgress) View.VISIBLE else View.GONE
            progressBar.progress = row.progress
            action.isEnabled = !row.inProgress
            action.text = when {
                row.inProgress -> PersianText.percent(row.progress)
                row.isDownloaded -> ctx.getString(R.string.downloaded_city)
                else -> ctx.getString(R.string.download_city)
            }
            action.setOnClickListener { if (!row.isDownloaded && !row.inProgress) onDownload(row.city) }
            delete.visibility = if (row.isDownloaded) View.VISIBLE else View.GONE
            delete.setOnClickListener { onDelete(row.city) }
        }
    }
}
