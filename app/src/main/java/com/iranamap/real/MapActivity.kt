package com.iranamap.real

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationBarView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.core.Categories
import com.iranamap.real.core.GeoMath
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.core.LanePlanner
import com.iranamap.real.core.ManeuverText
import com.iranamap.real.core.PersianText
import com.iranamap.real.core.PoiCategory
import com.iranamap.real.core.RouteBrief
import com.iranamap.real.core.ManeuverInfo
import com.iranamap.real.core.SpeedModel
import com.iranamap.real.core.SpeechPlanner
import com.iranamap.real.core.VoiceCatalog
import com.iranamap.real.data.CameraRepository
import com.iranamap.real.data.FavoritesRepository
import com.iranamap.real.data.PoiRepository
import com.iranamap.real.data.PoiResult
import com.iranamap.real.data.RouteRepository
import com.iranamap.real.data.SearchRepository
import com.iranamap.real.data.SpeedCamera
import com.iranamap.real.data.TrafficLight
import com.iranamap.real.data.TrafficLightRepository
import com.iranamap.real.map.LightMarker
import com.iranamap.real.map.MapController
import com.iranamap.real.map.PoiMarker
import com.iranamap.real.nav.ActiveRoute
import com.iranamap.real.nav.NavStateMapper
import com.iranamap.real.nav.RouteTracker
import com.iranamap.real.nav.TrackerState
import com.iranamap.real.service.NavigationService
import com.iranamap.real.settings.AppSettings
import com.iranamap.real.settings.MapStyleKind
import com.iranamap.real.settings.SettingsRepository
import com.iranamap.real.tts.Announcer
import com.iranamap.real.tts.SherpaSpeechEngine
import com.iranamap.real.tts.SpeechEngine
import com.iranamap.real.tts.VoiceInstaller
import com.iranamap.real.ui.CityDownloadActivity
import com.iranamap.real.ui.FavoritesActivity
import com.iranamap.real.ui.LaneGuidanceView
import com.iranamap.real.ui.RouteAdapter
import com.iranamap.real.ui.SearchActivity
import com.iranamap.real.ui.SettingsActivity
import com.iranamap.real.ui.VoiceSettingsActivity
import com.iranamap.real.util.hide
import com.iranamap.real.util.show
import com.iranamap.real.util.showIf
import com.iranamap.real.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Calendar

/**
 * صفحه‌ی اصلی: نقشه‌ی سه‌بعدی، جست‌وجو، مسیریابی نوبت‌به‌نوبت،
 * چراغ‌های راهنمایی هوشمند، دوربین سرعت و دستیار صوتی فارسی.
 */
class MapActivity : AppCompatActivity() {

    // ---------------------------------------------------------------- ویوها
    private lateinit var mapView: MapView
    private lateinit var etSearch: EditText
    private lateinit var btnVoice: ImageButton
    private lateinit var btnSearchSubmit: ImageButton
    private lateinit var bottomNav: NavigationBarView
    private lateinit var btnMyLocation: FloatingActionButton
    private lateinit var btn3D: FloatingActionButton
    private lateinit var btnLayers: FloatingActionButton
    private lateinit var btnTraffic: FloatingActionButton
    private lateinit var navPanel: MaterialCardView
    private lateinit var tvManeuverIcon: TextView
    private lateinit var tvManeuverDistance: TextView
    private lateinit var tvManeuverText: TextView
    private lateinit var tvStreetName: TextView
    private lateinit var laneGuidance: LaneGuidanceView
    private lateinit var tvEta: TextView
    private lateinit var tvRemainingDistance: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var btnStartNav: MaterialButton
    private lateinit var btnStopNav: MaterialButton
    private lateinit var btnAlternatives: MaterialButton
    private lateinit var btnCloseRoute: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var lightCard: MaterialCardView
    private lateinit var tvLightState: TextView
    private lateinit var tvLightCountdown: TextView
    private lateinit var tvLightDistance: TextView
    private lateinit var vRed: View
    private lateinit var vYellow: View
    private lateinit var vGreen: View
    private lateinit var speedCard: MaterialCardView
    private lateinit var tvSpeed: TextView
    private lateinit var tvLimit: TextView

    // ---------------------------------------------------------------- وابستگی‌ها
    private val settingsRepo by lazy { SettingsRepository.get(this) }
    private val favoritesRepo by lazy { FavoritesRepository.get(this) }
    private val searchRepo = SearchRepository()
    // قابل جایگزینی در تست دستگاه برای جلوگیری از وابستگی سناریوها به شبکه.
    private var routeRepo = RouteRepository()
    private val lightRepo = TrafficLightRepository()
    private val cameraRepo = CameraRepository()
    private val poiRepo = PoiRepository()
    private val mapController by lazy { MapController(this) }
    private val speechEngine: SpeechEngine by lazy { SherpaSpeechEngine(this) }
    private val announcer by lazy { Announcer(speechEngine) }
    private val planner = SpeechPlanner()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ---------------------------------------------------------------- وضعیت
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var settings = AppSettings()
    private var currentLocation: GeoPoint? = null
    private var currentBearing = 0.0
    private var lastLocation: Location? = null
    private var destination: GeoPoint? = null
    private var destinationName: String? = null
    private var destinationAddress: String? = null
    private var pendingDestination: GeoPoint? = null
    private var pendingDestinationName: String? = null

    private var activeRoute: ActiveRoute? = null
    private var alternativeRoutes: List<ActiveRoute> = emptyList()
    private var tracker: RouteTracker? = null
    private var isNavigating = false

    private var lights: List<TrafficLight> = emptyList()
    private var cameras: List<SpeedCamera> = emptyList()
    private var currentSpeedLimit: Int? = null

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var lightsJob: Job? = null
    private var camerasJob: Job? = null

    private var lastStyleApplied: MapStyleKind? = null
    private var lastVoiceId: String? = null
    private var lastLightsCenter: GeoPoint? = null
    private var lastCamerasCenter: GeoPoint? = null

    // ---------------------------------------------------------------- موقعیت
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleLocation(it) }
        }
    }

    private val locationRequest: LocationRequest by lazy {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(600L)
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
            moveToCurrentLocation()
        } else {
            toast(getString(R.string.permission_denied))
        }
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val lat = data.getDoubleExtra(SearchActivity.EXTRA_LAT, Double.NaN)
            val lng = data.getDoubleExtra(SearchActivity.EXTRA_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return@registerForActivityResult
            val title = data.getStringExtra(SearchActivity.EXTRA_TITLE).orEmpty()
            val address = data.getStringExtra(SearchActivity.EXTRA_ADDRESS).orEmpty()
            setDestination(GeoPoint(lat, lng), title, address)
        }
    }

    private val favoritesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val lat = data.getDoubleExtra(SearchActivity.EXTRA_LAT, Double.NaN)
            val lng = data.getDoubleExtra(SearchActivity.EXTRA_LNG, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) return@registerForActivityResult
            setDestination(
                GeoPoint(lat, lng),
                data.getStringExtra(SearchActivity.EXTRA_TITLE).orEmpty(),
                data.getStringExtra(SearchActivity.EXTRA_ADDRESS).orEmpty()
            )
        }
    }

    // ---------------------------------------------------------------- چرخه‌ی عمر
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        bindViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            configureMap()
            setupPoiTap(readyMap)
        }
        setupListeners()
        observeSettings()
        requestPermissionsIfNeeded()
        loadVoiceIfNeeded(settings.voiceId)
        handleIntent(intent)
    }

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        etSearch = findViewById(R.id.etSearch)
        btnVoice = findViewById(R.id.btnVoice)
        btnSearchSubmit = findViewById(R.id.btnSearchSubmit)
        bottomNav = findViewById(R.id.bottomNav)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        btn3D = findViewById(R.id.btn3D)
        btnLayers = findViewById(R.id.btnLayers)
        btnTraffic = findViewById(R.id.btnTraffic)
        navPanel = findViewById(R.id.navPanel)
        tvManeuverIcon = findViewById(R.id.tvManeuverIcon)
        tvManeuverDistance = findViewById(R.id.tvManeuverDistance)
        tvManeuverText = findViewById(R.id.tvManeuverText)
        tvStreetName = findViewById(R.id.tvStreetName)
        laneGuidance = findViewById(R.id.laneGuidance)
        tvEta = findViewById(R.id.tvEta)
        tvRemainingDistance = findViewById(R.id.tvRemainingDistance)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        btnStartNav = findViewById(R.id.btnStartNav)
        btnStopNav = findViewById(R.id.btnStopNav)
        btnAlternatives = findViewById(R.id.btnAlternatives)
        btnCloseRoute = findViewById(R.id.btnCloseRoute)
        btnShare = findViewById(R.id.btnShare)
        lightCard = findViewById(R.id.lightCard)
        tvLightState = findViewById(R.id.tvLightState)
        tvLightCountdown = findViewById(R.id.tvLightCountdown)
        tvLightDistance = findViewById(R.id.tvLightDistance)
        vRed = findViewById(R.id.vRed)
        vYellow = findViewById(R.id.vYellow)
        vGreen = findViewById(R.id.vGreen)
        speedCard = findViewById(R.id.speedCard)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvLimit = findViewById(R.id.tvLimit)
    }

    private fun configureMap() {
        val mapRef = map ?: return
        runCatching {
            mapRef.uiSettings.isCompassEnabled = true
            mapRef.uiSettings.isRotateGesturesEnabled = true
            mapRef.uiSettings.isTiltGesturesEnabled = true
            mapRef.uiSettings.isAttributionEnabled = true
        }
        loadStyle(settings.mapStyle)
    }

    private fun loadStyle(kind: MapStyleKind) {
        val mapRef = map ?: return
        lastStyleApplied = kind
        val dark = kind != MapStyleKind.LIGHT
        // تشخیص حالت کم‌مصرف برای Redmi 10C - سه‌بعدی سبک
        val isLite = try { com.iranamap.real.util.DevicePerformance.isLiteMode(this) } catch (_: Exception) { false }
        // در حالت کم‌مصرف، تنظیمات is3d را به صورت هوشمند غیرفعال کن تا کرش نکند
        val effective3d = if (isLite && settings.is3d) {
            // در lite mode، سه‌بعدی فقط با tilt کم فعال می‌شود
            settings.is3d
        } else settings.is3d
        mapRef.setStyle(Style.Builder().fromUri(MapController.styleUrl(kind))) { loaded ->
            style = loaded
            mapController.prepareStyle(loaded, dark, isLite)
            mapController.set3dEnabled(loaded, effective3d)
            mapController.setLightsVisible(loaded, settings.showTrafficLights)
            mapController.setCamerasVisible(loaded, settings.showCameras)
            activeRoute?.let { mapController.showRoute(loaded, it.points) }
            mapController.showAlternativeRoutes(
                loaded,
                alternativeRoutes.drop(1).map { it.points }
            )
            mapController.showDestination(loaded, destination)
            currentLocation?.let { mapController.showUserLocation(loaded, it) }
            cameraToStartPosition()
        }
    }

    private fun cameraToStartPosition() {
        val target = currentLocation?.let { org.maplibre.android.geometry.LatLng(it.lat, it.lng) }
            ?: org.maplibre.android.geometry.LatLng(35.6892, 51.3890)
        val position = CameraPosition.Builder()
            .target(target)
            .zoom(15.0)
            .tilt(if (settings.is3d) 45.0 else 0.0)
            .build()
        mapController.camera(position, map, animated = true, durationMs = 700)
    }

    // ---------------------------------------------------------------- تنظیمات
    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepo.settings.collect { newSettings ->
                val previousStyle = settings.mapStyle
                val previousVoice = settings.voiceId
                settings = newSettings
                planner.updateOptions(newSettings.announceOptions())
                btn3D.alpha = if (newSettings.is3d) 1f else 0.55f
                btnTraffic.alpha = if (newSettings.showTrafficLights) 1f else 0.55f
                if (lastStyleApplied != null && previousStyle != newSettings.mapStyle) {
                    loadStyle(newSettings.mapStyle)
                }
                if (previousVoice != newSettings.voiceId || lastVoiceId == null) {
                    loadVoiceIfNeeded(newSettings.voiceId)
                }
                speechEngine.setSpeed(newSettings.speechRate)
                speechEngine.setVolume(newSettings.speechVolume)
                style?.let {
                    mapController.set3dEnabled(it, newSettings.is3d)
                    mapController.setLightsVisible(it, newSettings.showTrafficLights)
                    mapController.setCamerasVisible(it, newSettings.showCameras)
                }
                if (!newSettings.showTrafficLights) {
                    lightCard.hide()
                    lightsJob?.cancel()
                    lastLightsCenter = null
                }
            }
        }
    }

    private fun loadVoiceIfNeeded(voiceId: String) {
        lastVoiceId = voiceId
        lifecycleScope.launch {
            val model = VoiceCatalog.find(voiceId) ?: VoiceCatalog.bundled
            val dir = runCatching { VoiceInstaller.ensureInstalled(model).getOrNull() }.getOrNull()
            if (dir == null) {
                android.util.Log.w("IranAmapTTS", "صدا نصب نشد: $voiceId")
                // در حالت کم‌رم، پیام ملایم‌تر نمایش بده
                toast(getString(R.string.speech_engine_missing) + " - در تنظیمات دوباره تلاش کنید")
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                runCatching { speechEngine.loadVoice(dir) }.getOrDefault(false)
            }
            if (!loaded) {
                android.util.Log.e("IranAmapTTS", "loadVoice failed for $voiceId dir=$dir")
                toast(getString(R.string.speech_engine_missing) + " - مدل خراب است")
            } else {
                android.util.Log.i("IranAmapTTS", "صدا آماده: $voiceId")
            }
        }
    }

    // ---------------------------------------------------------------- شنونده‌ها
    private fun setupListeners() {
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openSearch(etSearch.text.toString())
                true
            } else {
                false
            }
        }
        btnSearchSubmit.setOnClickListener { openSearch(etSearch.text.toString()) }
        btnVoice.setOnClickListener { startActivity(Intent(this, VoiceSettingsActivity::class.java)) }
        btnMyLocation.setOnClickListener { moveToCurrentLocation() }

        btn3D.setOnClickListener {
            val newValue = !settings.is3d
            lifecycleScope.launch { settingsRepo.set3d(newValue) }
            val mapRef = map ?: return@setOnClickListener
            val target = currentLocation?.let { org.maplibre.android.geometry.LatLng(it.lat, it.lng) }
                ?: mapRef.cameraPosition.target
            mapController.camera(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(mapRef.cameraPosition.zoom)
                    .tilt(if (newValue) 60.0 else 0.0)
                    .bearing(if (newValue) mapRef.cameraPosition.bearing else 0.0)
                    .build(),
                mapRef
            )
            toast(if (newValue) getString(R.string.map_3d) else getString(R.string.map_2d))
        }

        btnLayers.setOnClickListener {
            val next = when (settings.mapStyle) {
                MapStyleKind.DARK -> MapStyleKind.LIGHT
                MapStyleKind.LIGHT -> MapStyleKind.NEUTRAL
                MapStyleKind.NEUTRAL -> MapStyleKind.DARK
            }
            lifecycleScope.launch { settingsRepo.setMapStyle(next) }
        }

        btnTraffic.setOnClickListener {
            val newValue = !settings.showTrafficLights
            lifecycleScope.launch { settingsRepo.setShowTrafficLights(newValue) }
            toast(if (newValue) getString(R.string.show_traffic_lights_on_map) else getString(R.string.close))
        }

        btnStartNav.setOnClickListener { startNavigation() }
        btnStopNav.setOnClickListener { stopNavigation(speak = true) }
        btnCloseRoute.setOnClickListener { clearRoute() }
        btnShare.setOnClickListener { shareDestination() }
        btnAlternatives.setOnClickListener { showAlternativesSheet() }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tabSearch -> {
                    openSearch("")
                    false
                }
                R.id.tabSaved -> {
                    favoritesLauncher.launch(Intent(this, FavoritesActivity::class.java))
                    false
                }
                R.id.tabSettings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> true
            }
        }
    }

    // ---------------------------------------------------------------- جست‌وجو
    private fun openSearch(query: String) {
        hideKeyboard()
        val intent = Intent(this, SearchActivity::class.java).apply {
            currentLocation?.let {
                putExtra(SearchActivity.EXTRA_LAT, it.lat)
                putExtra(SearchActivity.EXTRA_LNG, it.lng)
            }
            if (query.isNotBlank()) putExtra(SearchActivity.EXTRA_TITLE, query)
        }
        searchLauncher.launch(intent)
    }

    private fun setDestination(point: GeoPoint, title: String, address: String) {
        if (!point.lat.isFinite() || !point.lng.isFinite() ||
            point.lat !in -90.0..90.0 || point.lng !in -180.0..180.0) return
        // انتخاب جدید باید درخواست قبلی و مقصد منتظر GPS را باطل کند.
        clearRoute()
        destination = point
        val name = title.ifBlank { getString(R.string.destination_set) }
        destinationName = name
        destinationAddress = address
        // اگر استایل هنوز آماده نیست، در loadStyle پس از بارگذاری دوباره رسم می‌شود.
        runCatching { mapController.showDestination(style, point) }
        runCatching { announcer.announce(planner.onDestinationSet(name)) }
        lifecycleScope.launch { favoritesRepo.recordVisit(name, address, point) }
        if (currentLocation == null) {
            pendingDestination = point
            pendingDestinationName = destinationName
            toast(getString(R.string.no_gps))
            moveToCurrentLocation()
        } else {
            requestRoute(point, destinationName)
        }
    }

    private fun requestRoute(dest: GeoPoint, name: String?) {
        val origin = currentLocation ?: run {
            pendingDestination = dest
            pendingDestinationName = name
            toast(getString(R.string.no_gps))
            moveToCurrentLocation()
            return
        }
        routeJob?.cancel()
        routeJob = lifecycleScope.launch {
            // نمایش لودینگ برای جلوگیری از سردرگمی کاربر
            toast("در حال محاسبه مسیر...")
            val result = routeRepo.fetchRoutes(origin, dest, name)
            result.onSuccess { routes ->
                // فیلتر مسیرهای معتبر: حداقل 2 نقطه و دارای steps
                val validRoutes = routes.filter { it.points.size >= 2 && it.totalMeters > 0 }
                if (validRoutes.isEmpty()) {
                    android.util.Log.w("IranAmapRoute", "هیچ مسیر معتبری از ${routes.size} مسیر دریافتی")
                    toast(getString(R.string.route_error) + " - هندسه نامعتبر")
                    return@onSuccess
                }
                alternativeRoutes = validRoutes
                validRoutes.firstOrNull()?.let { route ->
                    // محافظت کامل در برابر null برای جلوگیری از کرش Redmi 10C
                    runCatching {
                        activeRoute = route
                        tracker = RouteTracker(route)
                        mapController.showRoute(style, route.points)
                        mapController.showAlternativeRoutes(style, validRoutes.drop(1).map { it.points })
                        // overviewCamera ممکن است با bounds خالی کرش کند
                        runCatching { mapController.overviewCamera(map, route.points, settings.is3d) }
                            .onFailure { e -> android.util.Log.w("IranAmapRoute", "overviewCamera failed", e) }
                        showRoutePanel(route)
                        refreshSpeedLimit(route)
                    }.onFailure { e ->
                        android.util.Log.e("IranAmapRoute", "نمایش مسیر ناموفق", e)
                        toast("خطا در نمایش مسیر: ${e.message?.take(40)}")
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("IranAmapRoute", "fetchRoutes failed", e)
                toast(getString(R.string.route_error) + ": ${e.message?.take(50) ?: "خطای شبکه"}")
            }
        }
    }

    private fun refreshSpeedLimit(route: ActiveRoute) {
        currentSpeedLimit = route.steps.firstOrNull()?.let { step ->
            SpeedModel.parseMaxspeed(step.maxspeed)
        }
        tvLimit.text = SpeedModel.limitLabel(currentSpeedLimit)
    }

    private fun showRoutePanel(route: ActiveRoute) {
        navPanel.show()
        btnStartNav.show()
        btnStopNav.hide()
        btnAlternatives.showIf(alternativeRoutes.size > 1)
        val step = route.steps.getOrNull(1) ?: route.steps.firstOrNull()
        if (step != null) {
            tvManeuverIcon.text = ManeuverText.glyph(step.kind)
            tvManeuverText.text = ManeuverText.shortPhrase(step.kind, step.exitNumber)
            tvStreetName.text = ManeuverText.withRoadType(step.streetName).orEmpty()
            tvManeuverDistance.text = PersianText.formatDistance(step.distanceMeters)
            val plan = LanePlanner.plan(step.laneIndications, step.laneValid, step.kind)
            laneGuidance.setPlan(if (plan.lanes.isEmpty()) null else plan)
        }
        val trafficSeconds = trafficAwareSeconds(route.totalSeconds, route)
        tvRemainingDistance.text = PersianText.formatDistance(route.totalMeters)
        tvRemainingTime.text = PersianText.formatDuration(trafficSeconds)
        tvEta.text = getString(R.string.eta) + " " + etaText(trafficSeconds)
    }

    private fun showAlternativesSheet() {
        if (alternativeRoutes.size < 2) {
            toast(getString(R.string.alternative_routes) + ": " + PersianText.toPersianDigits("1"))
            return
        }
        val sheet = BottomSheetDialog(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = getString(R.string.alternative_routes)
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MapActivity, R.color.text_primary_dark))
            setPadding(0, 0, 0, 12)
        }
        container.addView(title)
        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = RouteAdapter { index ->
            alternativeRoutes.getOrNull(index)?.let { chosen ->
                activeRoute = chosen
                tracker = RouteTracker(chosen)
                mapController.showRoute(style, chosen.points)
                mapController.showAlternativeRoutes(
                    style, alternativeRoutes.filterIndexed { i, _ -> i != index }.map { it.points }
                )
                showRoutePanel(chosen)
                refreshSpeedLimit(chosen)
                mapController.overviewCamera(map, chosen.points, settings.is3d)
            }
            sheet.dismiss()
        }
        adapter.submit(alternativeRoutes)
        rv.adapter = adapter
        container.addView(rv)
        sheet.setContentView(container)
        sheet.show()
    }

    /** اشتراک‌گذاری مقصد با هر برنامه‌ی نصب‌شده روی گوشی. */
    private fun shareDestination() {
        val point = destination ?: run {
            currentLocation?.let { location ->
                sharePoint(location, getString(R.string.my_location))
            }
            return
        }
        sharePoint(point, destinationName ?: getString(R.string.destination_set))
    }

    private fun sharePoint(point: GeoPoint, title: String) {
        val text = getString(
            R.string.share_text,
            title,
            PersianText.toPersianDigits("%.5f".format(point.lat)),
            PersianText.toPersianDigits("%.5f".format(point.lng))
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share_location))) }
    }

    /** با ضربه روی نشانگرهای دسته‌بندی‌شده، گزینه‌های مسیریابی/ذخیره/اشتراک نمایش داده می‌شود. */
    private fun setupPoiTap(mapRef: MapLibreMap) {
        mapRef.addOnMapClickListener { latLng ->
            val tapped = GeoPoint(latLng.latitude, latLng.longitude)
            val nearest = pendingPois.minByOrNull { GeoMath.distanceMeters(tapped, it.point) }
            if (nearest != null && GeoMath.distanceMeters(tapped, nearest.point) < 120) {
                showPoiDialog(nearest)
                true
            } else {
                false
            }
        }
    }

    private fun showPoiDialog(poi: PoiResult) {
        val label = "${poi.glyph}  ${poi.name}\n" + PersianText.formatDistance(poi.distanceMeters)
        AlertDialog.Builder(this)
            .setTitle(poi.categoryTitle)
            .setMessage(label)
            .setPositiveButton(R.string.navigate_to) { _, _ ->
                setDestination(poi.point, poi.name, poi.address.orEmpty())
            }
            .setNeutralButton(R.string.favorite_add) { _, _ ->
                lifecycleScope.launch {
                    favoritesRepo.addPlace(poi.name, poi.address.orEmpty(), poi.point, poi.glyph)
                    toast(getString(R.string.favorite_added))
                }
            }
            .setNegativeButton(R.string.share_location) { _, _ ->
                sharePoint(poi.point, poi.name)
            }
            .show()
    }

    // ---------------------------------------------------------------- ناوبری
    private fun startNavigation() {
        val route = activeRoute ?: return
        if (isNavigating) return
        if (currentLocation == null) {
            toast(getString(R.string.no_gps))
            moveToCurrentLocation()
            return
        }
        isNavigating = true
        planner.reset()
        tracker = RouteTracker(route)
        btnStartNav.hide()
        btnStopNav.show()
        speedCard.show()
        startLocationUpdates()
        startForegroundService()
        // شروع راهنمایی نباید منتظر شبکه‌ی چراغ‌ها بماند؛ تعداد چراغ‌های کل مسیر هم فعلاً معلوم نیست.
        announcer.announce(
            planner.onRouteStart(
                RouteBrief(
                    destinationName = route.destinationName,
                    totalMeters = route.totalMeters,
                    totalSeconds = route.totalSeconds.toLong(),
                    lightCount = 0,
                    firstManeuver = route.steps.getOrNull(1)?.let {
                        ManeuverInfo(it.kind, it.streetName, it.exitNumber, it.startAlongMeters)
                    }
                )
            )
        )
    }

    private fun stopNavigation(speak: Boolean) {
        if (!isNavigating) return
        isNavigating = false
        planner.reset()
        if (speak) announcer.say(com.iranamap.real.core.VoicePhrases.navigationStopped(), 40)
        stopForegroundService()
        btnStopNav.hide()
        btnStartNav.showIf(activeRoute != null)
        lightCard.hide()
        resetLightVisuals()
    }

    private fun clearRoute() {
        routeJob?.cancel()
        routeJob = null
        pendingDestination = null
        pendingDestinationName = null
        lightsJob?.cancel()
        camerasJob?.cancel()
        lights = emptyList()
        cameras = emptyList()
        lastLightsCenter = null
        lastCamerasCenter = null
        stopNavigation(speak = false)
        activeRoute = null
        alternativeRoutes = emptyList()
        tracker = null
        destination = null
        destinationName = null
        destinationAddress = null
        navPanel.hide()
        speedCard.hide()
        lightCard.hide()
        mapController.clearRoute(style)
        mapController.clearAlternativeRoutes(style)
        mapController.showDestination(style, null)
        mapController.showLights(style, emptyList())
    }

    // ---------------------------------------------------------------- موقعیت
    private fun handleLocation(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        lastLocation = location
        currentLocation = point
        if (location.hasBearing()) currentBearing = location.bearing.toDouble()
        mapController.showUserLocation(style, point)
        val kmh = (location.speed * 3.6f).toInt().coerceAtLeast(0)
        tvSpeed.text = PersianText.toPersianDigits(kmh.toString())

        if (!isNavigating) {
            val pending = pendingDestination
            if (pending != null) {
                pendingDestination = null
                val name = pendingDestinationName
                pendingDestinationName = null
                requestRoute(pending, name)
            }
            return
        }

        val activeTracker = tracker ?: return
        val state = activeTracker.update(point, if (location.hasSpeed()) location.speed.toDouble() else null)
        updateNavigationUi(state)

        if (state.arrived) {
            announcer.announce(planner.onNavigation(navStateFor(state, kmh.toDouble())))
            stopNavigation(speak = false)
            toast(getString(R.string.arrived))
            return
        }

        if (state.offRoute && state.offRouteDistanceMeters > 55.0) {
            reroute(point)
            return
        }

        announcer.announce(planner.onNavigation(navStateFor(state, kmh.toDouble())))
        mapController.followPosition(map, state.snappedPoint, state.bearingDegrees, settings.is3d)
        refreshLights(point, state)
        refreshCameras(point)
    }

    private fun navStateFor(state: TrackerState, speedKmh: Double) = NavStateMapper.toNavState(
        state = state,
        step = NavStateMapper.upcomingStep(state, activeRoute),
        speedKmh = speedKmh,
        nowMillis = System.currentTimeMillis()
    )

    private fun updateNavigationUi(state: TrackerState) {
        val route = activeRoute ?: return
        val currentStep = route.steps.getOrNull(state.stepIndex)
        val nextStep = NavStateMapper.upcomingStep(state, route)
        val kind = nextStep?.kind ?: com.iranamap.real.core.ManeuverKind.CONTINUE
        tvManeuverIcon.text = ManeuverText.glyph(kind)
        tvManeuverText.text = ManeuverText.shortPhrase(kind, nextStep?.exitNumber)
        tvManeuverDistance.text = PersianText.formatDistance(state.distanceToManeuverMeters)
        tvStreetName.text = ManeuverText.withRoadType(nextStep?.streetName ?: state.currentStreetName).orEmpty()
        val trafficSeconds = trafficAwareSeconds(state.remainingSeconds.toDouble(), route)
        tvRemainingDistance.text = PersianText.formatDistance(state.remainingMeters)
        tvRemainingTime.text = PersianText.formatDuration(trafficSeconds)
        tvEta.text = getString(R.string.eta) + " " + etaText(trafficSeconds)
        val plan = nextStep?.let { LanePlanner.plan(it.laneIndications, it.laneValid, it.kind) }
        laneGuidance.setPlan(if (plan == null || plan.lanes.isEmpty()) null else plan)
        // تنها داده‌ی صریح maxspeed قابل نمایش است؛ نام خیابان حد مجاز واقعی را تعیین نمی‌کند.
        currentSpeedLimit = SpeedModel.parseMaxspeed(currentStep?.maxspeed)
        tvLimit.text = SpeedModel.limitLabel(currentSpeedLimit)
    }

    /** زمان باقی‌مانده با اعمال ترافیک تخمینی (بر اساس کلاس راه و ساعت روز). */
    private fun trafficAwareSeconds(baseSeconds: Double, route: ActiveRoute): Long {
        val roadClass = route.steps.firstOrNull()?.roadClass
        return SpeedModel.trafficAwareSeconds(baseSeconds, roadClass, secondsOfDay()).toLong()
    }

    private fun etaText(remainingSeconds: Long): String {
        val arrival = System.currentTimeMillis() + remainingSeconds * 1000L
        return PersianText.clockString(arrival)
    }

    private fun reroute(from: GeoPoint) {
        val dest = destination ?: return
        if (routeJob?.isActive == true) return
        routeJob = lifecycleScope.launch {
            announcer.announce(planner.onRerouted(0.0))
            routeRepo.fetchRoutes(from, dest, destinationName).onSuccess { routes ->
                alternativeRoutes = routes
                routes.firstOrNull()?.let { route ->
                    activeRoute = route
                    tracker = RouteTracker(route)
                    mapController.showRoute(style, route.points)
                    mapController.showAlternativeRoutes(style, routes.drop(1).map { it.points })
                    announcer.announce(planner.onRerouted(route.totalMeters))
                }
            }
        }
    }

    // ---------------------------------------------------------------- چراغ‌ها
    private fun refreshLights(point: GeoPoint, state: TrackerState) {
        if (!settings.showTrafficLights) {
            lightCard.hide()
            mapController.showLights(style, emptyList())
            return
        }
        val last = lastLightsCenter
        if ((last == null || GeoMath.distanceMeters(last, point) > 400) && lightsJob?.isActive != true) {
            lastLightsCenter = point
            val requestedRoute = activeRoute
            lightsJob = lifecycleScope.launch {
                val fetched = runCatching { lightRepo.lightsAround(point, 2500) }.getOrDefault(emptyList())
                if (isNavigating && activeRoute === requestedRoute && settings.showTrafficLights) {
                    lights = fetched
                    updateLightsOnRoute(state)
                }
            }
        }
        updateLightsOnRoute(state)
    }

    private fun updateLightsOnRoute(state: TrackerState) {
        val activeTracker = tracker ?: return
        val ahead = activeTracker.lightsAheadOnRoute(lights.map { it.id to it.point }, state.distanceAlong, 2500.0)
        val markers = ahead.take(8).map { (id, point, distance) -> LightMarker(id, point, distance) }
        mapController.showLights(style, markers)
        val nearest = ahead.firstOrNull()
        if (nearest == null) {
            lightCard.hide()
        } else {
            showLightCard(nearest.third)
        }
    }

    private fun showLightCard(distanceMeters: Double) {
        // موقعیت OSM زنده نیست؛ از نمایش رنگ و شمارش ساختگی جلوگیری می‌کنیم.
        lightCard.show()
        tvLightState.text = getString(R.string.traffic_light) + " • " + getString(R.string.light_unknown)
        tvLightCountdown.text = getString(R.string.light_obey_signal)
        tvLightDistance.text = PersianText.formatDistance(distanceMeters)
        resetLightVisuals()
    }

    private fun resetLightVisuals() {
        vRed.alpha = 0.22f
        vYellow.alpha = 0.22f
        vGreen.alpha = 0.22f
    }

    // ---------------------------------------------------------------- دوربین‌ها
    private fun refreshCameras(point: GeoPoint) {
        val last = lastCamerasCenter
        if (settings.showCameras && (last == null || GeoMath.distanceMeters(last, point) > 1000)) {
            if (camerasJob?.isActive != true) {
                lastCamerasCenter = point
                camerasJob = lifecycleScope.launch {
                    val fetched = runCatching { cameraRepo.camerasAround(point, 4000) }.getOrDefault(emptyList())
                    if (fetched.isNotEmpty()) {
                        cameras = fetched
                        mapController.showCameras(style, fetched.map { it.point })
                    }
                }
            }
        }
        val nearest = cameraRepo.nearestAhead(cameras, point, 1200.0) ?: return
        announcer.announce(
            planner.onSpeedAndCameras(
                speedKmh = currentSpeedKmh(),
                speedLimitKmh = currentSpeedLimit,
                cameraId = nearest.first.id,
                cameraDistanceMeters = nearest.second,
                cameraLimitKmh = nearest.first.maxspeedKmh,
                nowMillis = System.currentTimeMillis()
            )
        )
    }

    private fun currentSpeedKmh(): Double {
        val location = lastLocation
        return if (location != null && location.hasSpeed()) location.speed * 3.6 else 35.0
    }

    private fun secondsOfDay(): Long {
        val cal = Calendar.getInstance()
        return (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)).toLong()
    }

    // ---------------------------------------------------------------- مجوزها
    private fun requestPermissionsIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
            moveToCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            toast(getString(R.string.permission_denied))
        }
    }

    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    handleLocation(location)
                    mapController.followPosition(
                        map, GeoPoint(location.latitude, location.longitude),
                        if (location.hasBearing()) location.bearing.toDouble() else 0.0,
                        settings.is3d
                    )
                } else {
                    toast(getString(R.string.no_gps))
                }
            }
        } catch (e: SecurityException) {
            toast(getString(R.string.permission_denied))
        }
    }

    private fun startForegroundService() {
        try {
            val intent = Intent(this, NavigationService::class.java).apply {
                putExtra(NavigationService.EXTRA_DESTINATION, destinationName ?: "")
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            // ناوبری داخل برنامه ادامه می‌یابد
        }
    }

    private fun stopForegroundService() {
        runCatching { stopService(Intent(this, NavigationService::class.java)) }
    }

    // ---------------------------------------------------------------- intent
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        when (data.scheme) {
            "geo" -> {
                val parts = (data.schemeSpecificPart?.substringBefore('?') ?: return).split(",")
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull() ?: return
                    val lng = parts[1].toDoubleOrNull() ?: return
                    val name = data.getQueryParameter("q") ?: getString(R.string.destination_set)
                    setDestination(GeoPoint(lat, lng), name, "")
                }
            }
            "google.navigation" -> {
                val spec = data.schemeSpecificPart.orEmpty()
                val dest = when {
                    spec.contains("q=") -> spec.substringAfter("q=").substringBefore('&')
                    spec.contains("d=") -> spec.substringAfter("d=").substringBefore('&')
                    else -> return
                }
                val parts = dest.split(",")
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull() ?: return
                    val lng = parts[1].toDoubleOrNull() ?: return
                    setDestination(GeoPoint(lat, lng), getString(R.string.destination_set), "")
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()
    }

    // ---------------------------------------------------------------- چرخه‌ی عمر
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        bottomNav.selectedItemId = R.id.tabMap
        if (pendingPois.isNotEmpty()) {
            showPoisOnMap(pendingPois)
        }
    }

    /** نمایش نتایج دسته‌بندی‌شده‌ی صفحه‌ی جست‌وجو روی نقشه. */
    private fun showPoisOnMap(pois: List<PoiResult>) {
        mapController.showPois(
            style,
            pois.take(120).map { PoiMarker(it.point, it.glyph, it.name) }
        )
        pois.firstOrNull()?.let {
            mapController.camera(
                CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(it.point.lat, it.point.lng))
                    .zoom(14.0)
                    .tilt(if (settings.is3d) 40.0 else 0.0)
                    .build(),
                map
            )
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
        runCatching { if (isNavigating) stopNavigation(speak = false) }
        speechEngine.release()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    companion object {
        /** نقاط دسته‌بندی‌شده‌ای که صفحه‌ی جست‌وجو برای نمایش روی نقشه می‌گذارد. */
        var pendingPois: List<PoiResult> = emptyList()
    }
}
