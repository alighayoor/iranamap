package com.iranamap.real.map

import android.content.Context
import android.util.Log
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.LightColor
import com.iranamap.real.settings.MapStyleKind
import com.iranamap.real.util.toLatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** نشانگر یک نقطه‌ی دسته‌بندی‌شده روی نقشه. */
data class PoiMarker(
    val point: GeoPoint,
    val glyph: String,
    val name: String
)

data class LightMarker(
    val id: Long,
    val point: GeoPoint,
    val color: LightColor,
    val remainingSec: Int,
    val distanceMeters: Double
)

/** مدیریت لایه‌های نقشه: مسیر، مقصد، چراغ‌ها، ساختمان‌های سه‌بعدی و برچسب‌های فارسی. */
class MapController(private val context: Context) {

    companion object {
        const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
        const val STYLE_NEUTRAL = "https://tiles.openfreemap.org/styles/positron"

        private const val SRC_ROUTE = "iranamap-route"
        private const val SRC_ROUTE_GLOW = "iranamap-route-glow"
        private const val SRC_DEST = "iranamap-destination"
        private const val SRC_LIGHTS = "iranamap-lights"
        private const val SRC_USER = "iranamap-user"
        private const val SRC_ALTS = "iranamap-alternatives"
        private const val SRC_POIS = "iranamap-pois"
        private const val SRC_CAMERAS = "iranamap-cameras"
        private const val LAYER_USER_HALO = "iranamap-user-halo-layer"
        private const val LAYER_USER = "iranamap-user-layer"
        private const val LAYER_ROUTE_GLOW = "iranamap-route-glow-layer"
        private const val LAYER_ROUTE = "iranamap-route-layer"
        private const val LAYER_DEST = "iranamap-destination-layer"
        private const val LAYER_LIGHTS = "iranamap-lights-layer"
        private const val LAYER_LIGHTS_HALO = "iranamap-lights-halo-layer"
        private const val LAYER_EXTRA_3D = "iranamap-3d-buildings"
        private const val LAYER_ALTS = "iranamap-alternatives-layer"
        private const val LAYER_POIS = "iranamap-pois-layer"
        private const val LAYER_POIS_TEXT = "iranamap-pois-text-layer"
        private const val LAYER_CAMERAS = "iranamap-cameras-layer"

        fun styleUrl(kind: MapStyleKind): String = when (kind) {
            MapStyleKind.LIGHT -> STYLE_LIGHT
            MapStyleKind.DARK -> STYLE_DARK
            MapStyleKind.NEUTRAL -> STYLE_NEUTRAL
        }
    }

    private val tag = "IranAmapMap"

    /** آماده‌سازی استایل: برچسب فارسی، لایه‌های مسیر و چراغ - پشتیبانی حالت کم‌مصرف */
    fun prepareStyle(style: Style, dark: Boolean, liteMode: Boolean = false) {
        applyPersianLabels(style)
        ensureThreeDimensionalBuildings(style, dark, liteMode)
        addRouteLayers(style)
        addLightLayers(style)
        addDestinationLayer(style)
        addUserLayers(style)
        addAlternativeLayers(style)
        addPoiLayers(style)
        addCameraLayers(style)
    }

    /**
     * اولویت نام فارسی خیابان‌ها: name:fa ← name ← name:nonlatin.
     * استایل پایه به‌طور پیش‌فرض لاتین را هم نشان می‌دهد؛ اینجا فارسی اولویت می‌گیرد.
     */
    fun applyPersianLabels(style: Style) {
        val faName = Expression.get("name:fa")
        val plainName = Expression.get("name")
        val nonLatin = Expression.get("name:nonlatin")
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            try {
                val idLooksLikeLabel = layer.id.contains("name") || layer.id.contains("label") ||
                    layer.id.contains("place") || layer.id.contains("poi")
                if (!idLooksLikeLabel) continue
                val hasNameField = runCatching {
                    layer.textField?.expression?.toString()?.contains("name") == true
                }.getOrDefault(true)
                if (hasNameField) {
                    layer.setProperties(
                        PropertyFactory.textField(
                            Expression.coalesce(faName, plainName, nonLatin)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "تنظیم برچسب فارسی برای ${layer.id} ناموفق بود", e)
            }
        }
    }

    /** فعال/غیرفعال کردن ساختمان‌های سه‌بعدی - با حالت کم‌مصرف برای رم پایین (Redmi 10C). */
    fun ensureThreeDimensionalBuildings(style: Style, dark: Boolean, liteMode: Boolean = false) {
        // حالت کم‌مصرف: فقط رنگ لایه موجود را تغییر بده، لایه جدید نساز تا رم هدر نرود
        if (liteMode) {
            Log.i(tag, "حالت کم‌مصرف: ساختمان سه‌بعدی سبک")
        }
        val existing = style.getLayer("building-3d")
        if (existing != null) {
            try {
                if (!dark) {
                    existing.setProperties(
                        PropertyFactory.fillExtrusionColor("#8FA6C4"),
                        PropertyFactory.fillExtrusionOpacity(0.75f)
                    )
                } else {
                    existing.setProperties(
                        PropertyFactory.fillExtrusionColor("#1A233F"),
                        PropertyFactory.fillExtrusionOpacity(0.9f)
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "رنگ ساختمان‌های سه‌بعدی تنظیم نشد", e)
            }
            return
        }
        // در حالت کم‌مصرف، لایه اضافی نساز - فقط لایه موجود را استفاده کن
        if (liteMode) return
        try {
            val layer = FillExtrusionLayer(LAYER_EXTRA_3D, "openmaptiles")
            layer.setSourceLayer("building")
            layer.setProperties(
                PropertyFactory.fillExtrusionColor(if (dark) "#1A233F" else "#8FA6C4"),
                PropertyFactory.fillExtrusionHeight(
                    Expression.coalesce(
                        Expression.get("render_height"),
                        Expression.get("height"),
                        Expression.literal(12f)
                    )
                ),
                PropertyFactory.fillExtrusionOpacity(0.85f)
            )
            val below = firstSymbolLayerId(style)
            if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
        } catch (e: Exception) {
            Log.w(tag, "افزودن لایه‌ی سه‌بعدی ناموفق بود", e)
        }
    }

    fun set3dEnabled(style: Style?, enabled: Boolean) {
        val layer = style?.getLayer("building-3d") ?: style?.getLayer(LAYER_EXTRA_3D) ?: return
        try {
            layer.setProperties(
                PropertyFactory.visibility(if (enabled) Property.VISIBLE else Property.NONE)
            )
        } catch (e: Exception) {
            Log.w(tag, "تغییر وضعیت نمای سه‌بعدی ناموفق بود", e)
        }
    }

    private fun firstSymbolLayerId(style: Style): String? =
        style.layers.firstOrNull { it is SymbolLayer }?.id

    private fun addRouteLayers(style: Style) {
        if (style.getSource(SRC_ROUTE_GLOW) == null) {
            style.addSource(GeoJsonSource(SRC_ROUTE_GLOW))
        }
        if (style.getSource(SRC_ROUTE) == null) {
            style.addSource(GeoJsonSource(SRC_ROUTE))
        }
        if (style.getLayer(LAYER_ROUTE_GLOW) == null) {
            val glow = LineLayer(LAYER_ROUTE_GLOW, SRC_ROUTE_GLOW).withProperties(
                PropertyFactory.lineColor("#00E5FF"),
                PropertyFactory.lineWidth(22f),
                PropertyFactory.lineOpacity(0.22f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            style.addLayer(glow)
        }
        if (style.getLayer(LAYER_ROUTE) == null) {
            val line = LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#00D5F2"),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineOpacity(0.98f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            style.addLayer(line)
        }
    }

    private fun addDestinationLayer(style: Style) {
        if (style.getSource(SRC_DEST) == null) {
            style.addSource(GeoJsonSource(SRC_DEST))
        }
        if (style.getLayer(LAYER_DEST) == null) {
            val marker = CircleLayer(LAYER_DEST, SRC_DEST).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor("#FF3B30"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f)
            )
            style.addLayer(marker)
        }
    }

    private fun addLightLayers(style: Style) {
        if (style.getSource(SRC_LIGHTS) == null) {
            style.addSource(GeoJsonSource(SRC_LIGHTS))
        }
        if (style.getLayer(LAYER_LIGHTS_HALO) == null) {
            val halo = CircleLayer(LAYER_LIGHTS_HALO, SRC_LIGHTS).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleOpacity(0.25f)
            )
            style.addLayer(halo)
        }
        if (style.getLayer(LAYER_LIGHTS) == null) {
            val lights = CircleLayer(LAYER_LIGHTS, SRC_LIGHTS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f)
            )
            style.addLayer(lights)
        }
    }

    fun showRoute(style: Style?, points: List<GeoPoint>) {
        if (style == null || points.size < 2) return
        val coordinates = points.map { Point.fromLngLat(it.lng, it.lat) }
        val line = Feature.fromGeometry(LineString.fromLngLats(coordinates))
        val collection = FeatureCollection.fromFeatures(listOf(line))
        (style.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(collection)
        (style.getSource(SRC_ROUTE_GLOW) as? GeoJsonSource)?.setGeoJson(collection)
    }

    fun clearRoute(style: Style?) {
        if (style == null) return
        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
        (style.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(empty)
        (style.getSource(SRC_ROUTE_GLOW) as? GeoJsonSource)?.setGeoJson(empty)
    }

    fun showDestination(style: Style?, point: GeoPoint?) {
        if (style == null) return
        val source = style.getSource(SRC_DEST) as? GeoJsonSource ?: return
        if (point == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(point.lng, point.lat))
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    fun showLights(style: Style?, markers: List<LightMarker>) {
        if (style == null) return
        val source = style.getSource(SRC_LIGHTS) as? GeoJsonSource ?: return
        val features = markers.map { marker ->
            val feature = Feature.fromGeometry(Point.fromLngLat(marker.point.lng, marker.point.lat))
            feature.addStringProperty("color", colorHex(marker.color))
            feature.addNumberProperty("remaining", marker.remainingSec.toDouble())
            feature.addNumberProperty("id", marker.id.toDouble())
            feature
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setLightsVisible(style: Style?, visible: Boolean) {
        if (style == null) return
        val visibility = if (visible) Property.VISIBLE else Property.NONE
        try {
            style.getLayer(LAYER_LIGHTS)?.setProperties(PropertyFactory.visibility(visibility))
            style.getLayer(LAYER_LIGHTS_HALO)?.setProperties(PropertyFactory.visibility(visibility))
        } catch (e: Exception) {
            Log.w(tag, "تغییر نمایش چراغ‌ها ناموفق بود", e)
        }
    }

    private fun colorHex(color: LightColor): String = when (color) {
        LightColor.RED -> "#FF3B30"
        LightColor.YELLOW -> "#FFC300"
        LightColor.GREEN -> "#2ECC71"
    }

    private fun addUserLayers(style: Style) {
        if (style.getSource(SRC_USER) == null) {
            style.addSource(GeoJsonSource(SRC_USER))
        }
        if (style.getLayer(LAYER_USER_HALO) == null) {
            val halo = CircleLayer(LAYER_USER_HALO, SRC_USER).withProperties(
                PropertyFactory.circleRadius(22f),
                PropertyFactory.circleColor("#2E7BFF"),
                PropertyFactory.circleOpacity(0.22f)
            )
            style.addLayer(halo)
        }
        if (style.getLayer(LAYER_USER) == null) {
            val dot = CircleLayer(LAYER_USER, SRC_USER).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#2E7BFF"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f)
            )
            style.addLayer(dot)
        }
    }

    /** نمایش موقعیت فعلی کاربر به‌صورت نشانگر اختصاصی (بدون وابستگی به LocationComponent). */
    fun showUserLocation(style: Style?, point: GeoPoint?) {
        if (style == null) return
        val source = style.getSource(SRC_USER) as? GeoJsonSource ?: return
        if (point == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        val feature = Feature.fromGeometry(Point.fromLngLat(point.lng, point.lat))
        source.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    // -------------------------------------------------------------- مسیرهای جایگزین
    private fun addAlternativeLayers(style: Style) {
        if (style.getSource(SRC_ALTS) == null) style.addSource(GeoJsonSource(SRC_ALTS))
        if (style.getLayer(LAYER_ALTS) == null) {
            val layer = LineLayer(LAYER_ALTS, SRC_ALTS).withProperties(
                PropertyFactory.lineColor("#8E9BB0"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
            if (style.getLayer(LAYER_ROUTE_GLOW) != null) {
                style.addLayerBelow(layer, LAYER_ROUTE_GLOW)
            } else {
                style.addLayer(layer)
            }
        }
    }

    /** نمایش مسیرهای جایگزین؛ مسیر اصلی جداگانه با showRoute رسم می‌شود. */
    fun showAlternativeRoutes(style: Style?, routes: List<List<GeoPoint>>) {
        val source = style?.getSource(SRC_ALTS) as? GeoJsonSource ?: return
        val features = routes.filter { it.size >= 2 }.map { points ->
            Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) }))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun clearAlternativeRoutes(style: Style?) {
        val source = style?.getSource(SRC_ALTS) as? GeoJsonSource ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }

    // -------------------------------------------------------------- نقاط دسته‌بندی‌شده
    private fun addPoiLayers(style: Style) {
        if (style.getSource(SRC_POIS) == null) style.addSource(GeoJsonSource(SRC_POIS))
        if (style.getLayer(LAYER_POIS) == null) {
            val circle = CircleLayer(LAYER_POIS, SRC_POIS).withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor("#0B6E4F"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
            style.addLayer(circle)
        }
        if (style.getLayer(LAYER_POIS_TEXT) == null) {
            val text = SymbolLayer(LAYER_POIS_TEXT, SRC_POIS).withProperties(
                PropertyFactory.textField(Expression.get("glyph")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
            style.addLayer(text)
        }
    }

    fun showPois(style: Style?, pois: List<PoiMarker>) {
        val source = style?.getSource(SRC_POIS) as? GeoJsonSource ?: return
        val features = pois.map { poi ->
            val feature = Feature.fromGeometry(Point.fromLngLat(poi.point.lng, poi.point.lat))
            feature.addStringProperty("glyph", poi.glyph)
            feature.addStringProperty("name", poi.name)
            feature
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun clearPois(style: Style?) {
        val source = style?.getSource(SRC_POIS) as? GeoJsonSource ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }

    // -------------------------------------------------------------- دوربین سرعت
    private fun addCameraLayers(style: Style) {
        if (style.getSource(SRC_CAMERAS) == null) style.addSource(GeoJsonSource(SRC_CAMERAS))
        if (style.getLayer(LAYER_CAMERAS) == null) {
            val layer = CircleLayer(LAYER_CAMERAS, SRC_CAMERAS).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor("#FF9F0A"),
                PropertyFactory.circleStrokeColor("#12161F"),
                PropertyFactory.circleStrokeWidth(2f)
            )
            style.addLayer(layer)
        }
    }

    fun showCameras(style: Style?, cameras: List<GeoPoint>) {
        val source = style?.getSource(SRC_CAMERAS) as? GeoJsonSource ?: return
        val features = cameras.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setCamerasVisible(style: Style?, visible: Boolean) {
        try {
            style?.getLayer(LAYER_CAMERAS)?.setProperties(
                PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
            )
        } catch (e: Exception) {
            Log.w(tag, "تغییر نمایش دوربین‌ها ناموفق بود", e)
        }
    }

    fun camera(position: CameraPosition, map: MapLibreMap?, animated: Boolean = true, durationMs: Int = 900) {
        if (map == null) return
        try {
            if (animated) {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(position), durationMs)
            } else {
                map.cameraPosition = position
            }
        } catch (e: Exception) {
            Log.w(tag, "جابه‌جایی دوربین ناموفق بود", e)
        }
    }

    fun followPosition(
        map: MapLibreMap?,
        point: GeoPoint,
        bearing: Double,
        is3d: Boolean,
        zoom: Double = 17.0
    ) {
        val position = CameraPosition.Builder()
            .target(LatLng(point.lat, point.lng))
            .zoom(zoom)
            .tilt(if (is3d) 60.0 else 0.0)
            .bearing(if (is3d) bearing else 0.0)
            .build()
        camera(position, map, animated = true, durationMs = 850)
    }

    fun overviewCamera(map: MapLibreMap?, points: List<GeoPoint>, is3d: Boolean = false) {
        if (map == null || points.isEmpty()) return
        try {
            val builder = org.maplibre.android.geometry.LatLngBounds.Builder()
            points.forEach { builder.include(it.toLatLng()) }
            val bounds = builder.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 90, 220, 90, 320), 1200)
            Log.d(tag, "نمای کلی مسیر با ${points.size} نقطه")
        } catch (e: Exception) {
            Log.w(tag, "تنظیم نمای کلی مسیر ناموفق بود", e)
        }
    }
}
