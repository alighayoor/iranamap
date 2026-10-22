package com.iranamap.real.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.iranamap.real.R
import com.iranamap.real.core.PersianText
import com.iranamap.real.core.VoiceModel

data class VoiceRow(
    val model: VoiceModel,
    val installed: Boolean,
    val selected: Boolean,
    val downloading: Boolean = false,
    val progress: Int = 0
)

class VoiceAdapter(
    private val onAction: (VoiceModel) -> Unit,
    private val onPreview: (VoiceModel) -> Unit
) : RecyclerView.Adapter<VoiceAdapter.Holder>() {

    private val items = ArrayList<VoiceRow>()

    fun submit(rows: List<VoiceRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    fun updateProgress(voiceId: String, progress: Int) {
        val index = items.indexOfFirst { it.model.id == voiceId }
        if (index >= 0) {
            items[index] = items[index].copy(downloading = true, progress = progress)
            notifyItemChanged(index)
        }
    }

    fun markInstalled(voiceId: String) {
        val index = items.indexOfFirst { it.model.id == voiceId }
        if (index >= 0) {
            items[index] = items[index].copy(installed = true, downloading = false, progress = 100)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_voice, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvVoiceName)
        private val desc: TextView = itemView.findViewById(R.id.tvVoiceDesc)
        private val size: TextView = itemView.findViewById(R.id.tvVoiceSize)
        private val action: MaterialButton = itemView.findViewById(R.id.btnVoiceAction)
        private val preview: MaterialButton = itemView.findViewById(R.id.btnVoicePreview)
        private val progress: ProgressBar = itemView.findViewById(R.id.progressVoice)
        private val selected: ImageView = itemView.findViewById(R.id.ivSelected)

        fun bind(row: VoiceRow) {
            val ctx = itemView.context
            name.text = "${row.model.displayName} (${row.model.genderFa})"
            desc.text = row.model.description
            size.text = if (row.installed) ctx.getString(R.string.downloaded_city) else row.model.sizeText
            selected.visibility = if (row.selected) View.VISIBLE else View.INVISIBLE
            progress.visibility = if (row.downloading) View.VISIBLE else View.GONE
            progress.progress = row.progress
            action.isEnabled = !row.downloading
            action.text = when {
                row.downloading -> PersianText.percent(row.progress)
                row.installed -> if (row.selected) "انتخاب‌شده" else "انتخاب"
                else -> ctx.getString(R.string.voice_download)
            }
            action.setOnClickListener {
                if (row.installed) {
                    itemView.performClick()
                } else {
                    onAction(row.model)
                }
            }
            preview.isEnabled = row.installed
            preview.setOnClickListener { onPreview(row.model) }
            itemView.setOnClickListener {
                if (row.installed) onPreview(row.model)
            }
        }
    }
}
