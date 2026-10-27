package com.iranamap.real.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.iranamap.real.MapActivity
import com.iranamap.real.R
import com.iranamap.real.core.Categories
import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.PersianText
import com.iranamap.real.core.PoiCategory
import com.iranamap.real.data.PoiRepository
import com.iranamap.real.data.SearchRepository
import com.iranamap.real.util.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * جست‌وجو و کشف مکان: جست‌وجوی متنی (Nominatim) + دسته‌بندی‌ها (Overpass)
 * معادل صفحه‌ی «جست‌وجو / 附近» در آمپ.
 */
class SearchActivity : AppCompatActivity() {

    private val searchRepo = SearchRepository()
    private val poiRepo = PoiRepository()

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var rvResults: RecyclerView
    private lateinit var tvHeader: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var chipGroup: ChipGroup
    private lateinit var adapter: PlaceAdapter

    private var center: GeoPoint? = null
    private var activeCategory: PoiCategory? = null
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etSearch = findViewById(R.id.etSearch)
        btnClear = findViewById(R.id.btnClear)
        rvResults = findViewById(R.id.rvResults)
        tvHeader = findViewById(R.id.tvResultsHeader)
        tvEmpty = findViewById(R.id.tvEmpty)
        progress = findViewById(R.id.progress)
        chipGroup = findViewById(R.id.chipCategories)

        center = intent?.let {
            val lat = it.getDoubleExtra(EXTRA_LAT, Double.NaN)
            val lng = it.getDoubleExtra(EXTRA_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) null else GeoPoint(lat, lng)
        }

        adapter = PlaceAdapter { row -> returnResult(row.point, row.title, row.address.ifBlank { row.subtitle }) }
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        buildCategoryChips()

        btnClear.setOnClickListener {
            etSearch.setText("")
            activeCategory = null
            chipGroup.clearCheck()
            clearResults()
        }
        findViewById<ImageButton>(R.id.btnSubmitSearch).setOnClickListener {
            runTextSearch(etSearch.text.toString())
            hideKeyboard()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // جست‌وجوی عمومی Nominatim نباید برای هر حرف به صورت autocomplete فراخوانی شود.
                // همچنین نتیجه‌ی درخواست قبلی پس از ویرایش نباید روی متن جدید نمایش داده شود.
                activeCategory = null
                chipGroup.clearCheck()
                clearResults()
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runTextSearch(etSearch.text.toString())
                true
            } else {
                false
            }
        }

        // اگر دسته‌ای از صفحه‌ی نقشه پاس داده شده باشد، همان را اجرا کن
        val categoryId = intent?.getStringExtra(EXTRA_CATEGORY)
        if (categoryId != null) {
            Categories.find(categoryId)?.let { loadCategory(it) }
        } else {
            val initialQuery = intent?.getStringExtra(EXTRA_TITLE).orEmpty().trim()
            etSearch.requestFocus()
            if (initialQuery.isNotBlank()) {
                etSearch.setText(initialQuery)
                etSearch.setSelection(initialQuery.length)
                runTextSearch(initialQuery)
            } else {
                showKeyboard()
            }
        }
    }

    private fun clearResults() {
        job?.cancel()
        job = null
        progress.visibility = View.GONE
        adapter.clear()
        tvHeader.visibility = View.GONE
        tvEmpty.visibility = View.GONE
    }

    private fun buildCategoryChips() {
        Categories.quick.forEach { category ->
            val chip = Chip(this).apply {
                text = "${category.glyph}  ${category.titleFa}"
                isCheckable = true
                setOnClickListener {
                    if (isChecked) loadCategory(category) else {
                        activeCategory = null
                        clearResults()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun loadCategory(category: PoiCategory) {
        val origin = center ?: run {
            toast(getString(R.string.no_gps))
            return
        }
        activeCategory = category
        job?.cancel()
        job = lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            val results = poiRepo.byCategory(category, origin, radiusMeters = 3000)
            progress.visibility = View.GONE
            val rows = results.map { poi ->
                PlaceRow(
                    title = poi.name,
                    subtitle = poi.address.orEmpty(),
                    point = poi.point,
                    distanceMeters = poi.distanceMeters,
                    glyph = poi.glyph,
                    address = poi.address.orEmpty()
                )
            }
            adapter.submit(rows)
            rvResults.scrollToPosition(0)
            tvHeader.text = getString(R.string.category_results, PersianText.toPersianDigits(rows.size.toString()))
            tvHeader.visibility = View.VISIBLE
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            // نمایش روی نقشه
            MapActivity.pendingPois = results
        }
    }

    private fun runTextSearch(query: String) {
        val q = query.trim()
        activeCategory = null
        chipGroup.clearCheck()
        clearResults()
        if (q.length < 2) return
        job = lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            val results = searchRepo.search(q, center)
            progress.visibility = View.GONE
            val rows = results.map { result ->
                PlaceRow(
                    title = result.title,
                    subtitle = result.subtitle,
                    point = result.point,
                    distanceMeters = center?.let { GeoMath.distanceMeters(it, result.point) },
                    glyph = if (result.isCity) "🏙" else "📍",
                    address = result.subtitle
                )
            }
            adapter.submit(rows)
            rvResults.scrollToPosition(0)
            tvHeader.text = getString(R.string.category_results, PersianText.toPersianDigits(rows.size.toString()))
            tvHeader.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
            tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun returnResult(point: GeoPoint, title: String, address: String) {
        hideKeyboard()
        val data = Intent().apply {
            putExtra(EXTRA_LAT, point.lat)
            putExtra(EXTRA_LNG, point.lng)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ADDRESS, address)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CATEGORY = "category"
    }
}
