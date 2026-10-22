package com.iranamap.real.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.iranamap.real.R
import com.iranamap.real.core.HistoryEntry
import com.iranamap.real.core.SavedPlace
import com.iranamap.real.data.FavoritesRepository
import com.iranamap.real.util.toast
import kotlinx.coroutines.launch

/** ذخیره‌ها و تاریخچه‌ی مسیرها (معادل «我的/收藏/足迹» در آمپ). */
class FavoritesActivity : AppCompatActivity() {

    private val repo by lazy { FavoritesRepository.get(this) }
    private lateinit var favoritesAdapter: FavoriteAdapter
    private lateinit var historyAdapter: FavoriteAdapter
    private lateinit var rvFavorites: RecyclerView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnClearHistory: MaterialButton
    private lateinit var tabs: TabLayout

    private var favorites: List<SavedPlace> = emptyList()
    private var history: List<HistoryEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        rvFavorites = findViewById(R.id.rvFavorites)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        tabs = findViewById(R.id.tabs)

        favoritesAdapter = FavoriteAdapter(
            onNavigate = { row -> navigateTo(row.point.lat, row.point.lng, row.title) },
            onDelete = { row -> confirmDelete(row) }
        )
        historyAdapter = FavoriteAdapter(
            onNavigate = { row -> navigateTo(row.point.lat, row.point.lng, row.title) },
            onDelete = { _ -> }
        )
        rvFavorites.layoutManager = LinearLayoutManager(this)
        rvFavorites.adapter = favoritesAdapter
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        tabs.addTab(tabs.newTab().setText(R.string.favorites))
        tabs.addTab(tabs.newTab().setText(R.string.history))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                repo.clearHistory()
                toast(getString(R.string.history_cleared))
            }
        }

        lifecycleScope.launch {
            repo.places.collect { list ->
                favorites = list
                if (tabs.selectedTabPosition == 0) bindFavorites()
            }
        }
        lifecycleScope.launch {
            repo.history.collect { list ->
                history = list
                if (tabs.selectedTabPosition == 1) bindHistory()
            }
        }

        showTab(0)
    }

    private fun showTab(position: Int) {
        val isFavorites = position == 0
        rvFavorites.visibility = if (isFavorites) View.VISIBLE else View.GONE
        rvHistory.visibility = if (isFavorites) View.GONE else View.VISIBLE
        btnClearHistory.visibility = if (isFavorites) View.GONE else View.VISIBLE
        if (isFavorites) bindFavorites() else bindHistory()
    }

    private fun bindFavorites() {
        val rows = favorites.map { place ->
            FavoriteRow(
                id = place.id,
                title = place.shortLabel,
                subtitle = place.address.ifBlank { "%.5f, %.5f".format(place.point.lat, place.point.lng) },
                point = place.point,
                icon = place.icon
            )
        }
        favoritesAdapter.submit(rows)
        tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = getString(R.string.favorite_empty_hint)
    }

    private fun bindHistory() {
        val rows = history.map { entry ->
            FavoriteRow(
                id = entry.id,
                title = entry.label.ifBlank { entry.address },
                subtitle = com.iranamap.real.core.PersianText.jalaliShort(entry.visitedAtMillis),
                point = entry.point,
                icon = "🕘",
                deletable = false
            )
        }
        historyAdapter.submit(rows)
        tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = getString(R.string.history_empty)
    }

    private fun confirmDelete(row: FavoriteRow) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(row.title)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repo.removePlace(row.id)
                    toast(getString(R.string.favorite_removed))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun navigateTo(lat: Double, lng: Double, title: String) {
        val data = Intent().apply {
            putExtra(SearchActivity.EXTRA_LAT, lat)
            putExtra(SearchActivity.EXTRA_LNG, lng)
            putExtra(SearchActivity.EXTRA_TITLE, title)
        }
        setResult(RESULT_OK, data)
        finish()
    }
}
