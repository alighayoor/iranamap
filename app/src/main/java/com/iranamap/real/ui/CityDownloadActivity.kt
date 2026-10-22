package com.iranamap.real.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.iranamap.real.R
import com.iranamap.real.core.City
import com.iranamap.real.core.CityCatalog
import com.iranamap.real.core.OfflinePlanner
import com.iranamap.real.core.PersianText
import com.iranamap.real.map.MapController
import com.iranamap.real.offline.OfflineRepository
import com.iranamap.real.settings.SettingsRepository
import com.iranamap.real.util.toast
import kotlinx.coroutines.launch

/** دانلود انتخابی نقشه‌ی شهرهای ایران برای استفاده‌ی آفلاین. */
class CityDownloadActivity : AppCompatActivity() {

    private val repo by lazy { OfflineRepository(this) }
    private val settingsRepo by lazy { SettingsRepository.get(this) }
    private lateinit var adapter: CityAdapter
    private lateinit var tvStorage: TextView
    private lateinit var etSearch: EditText
    private var selectedProvince: String? = null
    private var query: String = ""
    private val downloadedSizes = HashMap<String, Long>()
    private val activeDownloads = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city)

        tvStorage = findViewById(R.id.tvStorageInfo)
        etSearch = findViewById(R.id.etCitySearch)
        val rv: RecyclerView = findViewById(R.id.rvCities)
        adapter = CityAdapter(
            onDownload = { city -> downloadCity(city) },
            onDelete = { city -> confirmDelete(city) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        setupProvinceChips()
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refresh()
            }
        })

        lifecycleScope.launch {
            loadDownloadedStates()
        }
    }

    private fun setupProvinceChips() {
        val group: ChipGroup = findViewById(R.id.chipProvinces)
        fun addChip(label: String, province: String?) {
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = province == null
                setOnClickListener {
                    selectedProvince = province
                    refresh()
                }
            }
            group.addView(chip)
        }
        addChip(getString(R.string.all_provinces), null)
        CityCatalog.provinces.forEach { province -> addChip(province, province) }
    }

    private fun refresh() {
        val cities = CityCatalog.search(query, selectedProvince)
        val rows = cities.map { city ->
            CityRow(
                city = city,
                downloadedSizeBytes = downloadedSizes[city.id] ?: 0L,
                inProgress = activeDownloads.contains(city.id)
            )
        }
        adapter.submit(rows)
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        val total = downloadedSizes.values.sum()
        val planned = CityCatalog.search(query, selectedProvince).sumOf { OfflinePlanner.planForCity(it).totalBytes }
        tvStorage.text = getString(R.string.storage_used, PersianText.fileSize(total)) +
            " • " + PersianText.toPersianDigits(CityCatalog.search(query, selectedProvince).size.toString()) +
            " شهر • " + PersianText.fileSize(planned)
    }

    private suspend fun loadDownloadedStates() {
        val regions = repo.listRegions()
        for (region in regions) {
            val cityId = region.cityId ?: continue
            val size = repo.cachedSizeOf(cityId)
            if (size > 0) downloadedSizes[cityId] = size
        }
        refresh()
    }

    private fun downloadCity(city: City) {
        if (activeDownloads.contains(city.id)) return
        val plan = OfflinePlanner.planForCity(city)
        if (plan.totalBytes > 400L * 1024 * 1024) {
            AlertDialog.Builder(this)
                .setTitle(R.string.warning)
                .setMessage(getString(R.string.download_all_warning) + "\n\n" + OfflinePlanner.humanSummary(plan))
                .setPositiveButton(R.string.ok) { _, _ -> performDownload(city) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        performDownload(city)
    }

    private fun performDownload(city: City) {
        activeDownloads.add(city.id)
        refresh()
        lifecycleScope.launch {
            val settings = settingsRepo.current()
            val styleUrl = MapController.styleUrl(settings.mapStyle)
            toast(getString(R.string.downloading) + " " + city.name)
            val result = repo.downloadCity(city, styleUrl) { progress ->
                runOnUiThread {
                    adapter.updateProgress(city.id, progress.percent)
                    tvStorage.text = getString(
                        R.string.download_progress,
                        progress.percent,
                        PersianText.fileSize(progress.bytes),
                        PersianText.fileSize(OfflinePlanner.planForCity(city).totalBytes)
                    )
                }
            }
            activeDownloads.remove(city.id)
            result.onSuccess {
                val size = repo.cachedSizeOf(city.id)
                downloadedSizes[city.id] = if (size > 0) size else OfflinePlanner.planForCity(city).totalBytes
                adapter.markDownloaded(city.id, downloadedSizes[city.id] ?: 0L)
                toast(city.name + ": " + getString(R.string.download_complete))
            }.onFailure { error ->
                adapter.submit(
                    CityCatalog.search(query, selectedProvince).map {
                        CityRow(it, downloadedSizes[it.id] ?: 0L, activeDownloads.contains(it.id))
                    }
                )
                toast(getString(R.string.download_failed) + ": " + (error.message ?: ""))
                refresh()
            }
            updateStorageInfo()
        }
    }

    private fun confirmDelete(city: City) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_region)
            .setMessage(city.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repo.deleteRegionsOfCity(city.id)
                        .onSuccess {
                            downloadedSizes.remove(city.id)
                            adapter.markDeleted(city.id)
                            updateStorageInfo()
                            toast(getString(R.string.delete))
                        }
                        .onFailure { toast(it.message) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
