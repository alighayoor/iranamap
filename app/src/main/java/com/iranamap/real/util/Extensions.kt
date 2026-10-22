package com.iranamap.real.util

import android.content.Context
import android.view.View
import android.widget.Toast
import com.iranamap.real.core.GeoPoint
import org.maplibre.android.geometry.LatLng

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

fun View.show() {
    if (visibility != View.VISIBLE) visibility = View.VISIBLE
}

fun View.hide() {
    if (visibility != View.GONE) visibility = View.GONE
}

fun View.showIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun Context.toast(message: CharSequence?) {
    if (!message.isNullOrBlank()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun GeoPoint.toLatLng(): LatLng = LatLng(lat, lng)

fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

val GeoPoint.maplibre: LatLng get() = toLatLng()

fun String?.orDash(): String = if (isNullOrBlank()) "—" else this

/** تبدیل سرعت متر بر ثانیه به کیلومتر بر ساعت. */
fun mpsToKmh(mps: Float): Int = (mps * 3.6f).toInt()
