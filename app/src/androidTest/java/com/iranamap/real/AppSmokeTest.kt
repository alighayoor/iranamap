package com.iranamap.real

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.core.CameraKind
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.data.SpeedCamera
import com.iranamap.real.data.CameraRepository
import com.iranamap.real.data.OverpassApi
import com.iranamap.real.data.OverpassResponse
import com.iranamap.real.settings.AppSettings
import com.iranamap.real.data.OsrmApi
import com.iranamap.real.data.OsrmGeometry
import com.iranamap.real.data.OsrmLeg
import com.iranamap.real.data.OsrmManeuver
import com.iranamap.real.data.OsrmResponse
import com.iranamap.real.data.OsrmRoute
import com.iranamap.real.data.OsrmStep
import com.iranamap.real.data.RouteRepository
import com.iranamap.real.ui.CityDownloadActivity
import com.iranamap.real.ui.FavoritesActivity
import com.iranamap.real.ui.SearchActivity
import com.iranamap.real.ui.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val origin = GeoPoint(29.620, 52.520)
    private val destination = GeoPoint(29.630, 52.530)

    private class FakeOsrm : OsrmApi {
        override suspend fun route(
            coords: String, overview: String, geometries: String,
            steps: Boolean, alternatives: Boolean, annotations: String
        ): OsrmResponse = OsrmResponse(
            code = "Ok",
            routes = listOf(OsrmRoute(
                distance = 1500.0,
                duration = 240.0,
                geometry = OsrmGeometry(coordinates = listOf(
                    listOf(52.520, 29.620), listOf(52.526, 29.626), listOf(52.530, 29.630)
                )),
                legs = listOf(OsrmLeg(steps = listOf(
                    OsrmStep(distance = 1000.0, name = "خیابان اول", maneuver = OsrmManeuver(type = "depart")),
                    OsrmStep(distance = 500.0, name = "خیابان دوم", maneuver = OsrmManeuver(type = "turn", modifier = "right")),
                    OsrmStep(distance = 0.0, name = "مقصد", maneuver = OsrmManeuver(type = "arrive"))
                )))
            ))
        )
        override suspend fun nearest(coords: String, number: Int): OsrmResponse = OsrmResponse()
    }

    private fun setField(activity: MapActivity, name: String, value: Any?) {
        MapActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun getField(activity: MapActivity, name: String): Any? =
        MapActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun invoke(activity: MapActivity, name: String, types: Array<Class<*>>, vararg args: Any?) {
        MapActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *args)
    }

    private fun assertRtlRoot(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        assertEquals("The Persian screen should be RTL regardless of system language", View.LAYOUT_DIRECTION_RTL, root.layoutDirection)
    }

    private fun chooseDestination(activity: MapActivity) {
        invoke(activity, "setDestination", arrayOf(GeoPoint::class.java, String::class.java, String::class.java), destination, "مقصد آزمایشی", "شیراز")
    }

    private fun installFakeRouteService(activity: MapActivity) {
        val fake = FakeOsrm()
        setField(activity, "routeRepo", RouteRepository(fake, fake))
    }

    @Test
    fun packageLabelDistinguishesTheSideBySideBuild() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        if (context.packageName == "com.iranamap.real.qa") {
            assertTrue("The QA app must be recognizable in the launcher", label.contains("آزمایشی"))
        } else {
            assertEquals("نقشه ایران", label)
        }
    }

    @Test
    fun favoritesScreenUsesPersianLayoutDirection() {
        ActivityScenario.launch(FavoritesActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertRtlRoot(activity) }
        }
    }

    @Test
    fun searchScreenKeepsTextSentFromMapAndClearsIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_TITLE, "شیراز")
        ActivityScenario.launch<SearchActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertRtlRoot(activity)
                val text = activity.findViewById<EditText>(R.id.etSearch)
                assertEquals("شیراز", text.text.toString())
                activity.findViewById<View>(R.id.btnClear).performClick()
                assertEquals("", text.text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.progress).visibility)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun settingsDoNotOfferUnverifiedLiveTrafficLightAnnouncements() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertRtlRoot(activity)
                val option = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swLights)
                assertFalse(option.isEnabled)
                assertFalse(option.isChecked)
                assertTrue(option.text.toString().contains("دادهٔ زنده"))
                val traffic = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swTraffic)
                assertFalse(traffic.isEnabled)
                assertFalse(traffic.isChecked)
                val limit = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swSpeedLimit)
                assertFalse(limit.isEnabled)
                assertFalse(limit.isChecked)
                assertTrue(limit.text.toString().contains("دادهٔ معتبر"))
            }
        }
    }

    @Test
    fun disabledCameraSettingDoesNotAnnouncePreviouslyLoadedCameras() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "settings", AppSettings(showCameras = false))
                setField(activity, "cameras", listOf(SpeedCamera(
                    id = 42L, point = origin, kind = CameraKind.SPEED,
                    maxspeedKmh = 50, name = null
                )))
                val planner = getField(activity, "planner")!!
                val spoken = planner.javaClass.getDeclaredField("cameraSpokenAt")
                    .apply { isAccessible = true }.get(planner) as Map<*, *>
                assertTrue(spoken.isEmpty())
                invoke(activity, "refreshCameras", arrayOf(GeoPoint::class.java), origin)
                assertTrue("A disabled camera setting must suppress stale-camera announcements", spoken.isEmpty())
            }
        }
    }

    @Test
    fun failedCameraLookupBacksOffInsteadOfPretendingEmptySuccess() {
        val attempts = AtomicInteger()
        val failing = object : OverpassApi {
            override suspend fun query(data: String): OverpassResponse {
                attempts.incrementAndGet()
                throw IOException("no network in test")
            }
        }
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "cameraRepo", CameraRepository(failing))
                setField(activity, "style", null)
                setField(activity, "settings", AppSettings(showCameras = true))
                setField(activity, "isNavigating", true)
                invoke(activity, "refreshCameras", arrayOf(GeoPoint::class.java), origin)
            }
            var waitingToRetry = false
            for (attempt in 1..50) {
                scenario.onActivity { activity ->
                    waitingToRetry = (getField(activity, "retryCamerasAtElapsedMs") as Long) > SystemClock.elapsedRealtime()
                }
                if (waitingToRetry) break
                Thread.sleep(50)
            }
            assertTrue("Network failure should schedule a bounded retry", waitingToRetry)
            scenario.onActivity { activity ->
                assertNull(getField(activity, "lastCamerasCenter"))
                invoke(activity, "refreshCameras", arrayOf(GeoPoint::class.java), origin)
                setField(activity, "isNavigating", false)
            }
            Thread.sleep(150)
            assertEquals("A retry must not immediately repeat the failed request", 1, attempts.get())
        }
    }

    @Test
    fun speedIsUnknownWhenLocationDoesNotSupplyIt() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "style", null)
                val location = Location("test").apply {
                    latitude = origin.lat
                    longitude = origin.lng
                    accuracy = 5f
                }
                invoke(activity, "handleLocation", arrayOf(Location::class.java), location)
                assertEquals("—", activity.findViewById<TextView>(R.id.tvSpeed).text.toString())
                location.speed = 10f // metres per second = 36 km/h
                invoke(activity, "handleLocation", arrayOf(Location::class.java), location)
                assertEquals("۳۶", activity.findViewById<TextView>(R.id.tvSpeed).text.toString())
            }
        }
    }

    @Test
    fun offlineCityScreenOpensAndFiltersCities() {
        ActivityScenario.launch(CityDownloadActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertRtlRoot(activity)
                activity.findViewById<EditText>(R.id.etCitySearch).setText("شیراز")
                val list = activity.findViewById<RecyclerView>(R.id.rvCities)
                assertTrue("City search should contain Shiraz", (list.adapter?.itemCount ?: 0) > 0)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun destinationCanBeSetAndClearedBeforeMapOrGpsAreReady() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertRtlRoot(activity)
                installFakeRouteService(activity)
                setField(activity, "style", null)
                setField(activity, "currentLocation", null)
                chooseDestination(activity)
                assertEquals(destination, getField(activity, "destination"))
                assertFalse(activity.isFinishing)
                invoke(activity, "clearRoute", emptyArray())
                assertNull(getField(activity, "destination"))
                assertNull(getField(activity, "pendingDestination"))
            }
        }
    }

    @Test
    fun destinationRouteStartTurnAndStopWorkOnDevice() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                installFakeRouteService(activity)
                // The request must work even before the map style is available.
                setField(activity, "style", null)
                setField(activity, "currentLocation", origin)
                chooseDestination(activity)
            }
            var routeVisible = false
            for (attempt in 1..100) {
                scenario.onActivity { routeVisible = it.findViewById<View>(R.id.navPanel).visibility == View.VISIBLE }
                if (routeVisible) break
                Thread.sleep(100)
            }
            assertTrue("Route panel did not appear", routeVisible)
            scenario.onActivity { activity ->
                val instruction = activity.findViewById<TextView>(R.id.tvManeuverText)
                assertTrue(instruction.text.toString().contains("راست"))
                activity.findViewById<View>(R.id.btnStartNav).performClick()
                assertEquals(true, getField(activity, "isNavigating"))
                val location = Location("test").apply {
                    latitude = origin.lat
                    longitude = origin.lng
                    accuracy = 5f
                    speed = 10f
                }
                invoke(activity, "handleLocation", arrayOf(Location::class.java), location)
                assertTrue("The upcoming turn should be displayed well before the junction", instruction.text.toString().contains("راست"))
                activity.findViewById<View>(R.id.btnStopNav).performClick()
                assertEquals(false, getField(activity, "isNavigating"))
                activity.findViewById<View>(R.id.btnCloseRoute).performClick()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.navPanel).visibility)
                assertNull(getField(activity, "destination"))
            }
            // Android may report the foreground-service timeout several seconds later.
            Thread.sleep(6500)
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun unknownTrafficLightNeverShowsInventedColorOrCountdown() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                invoke(activity, "showLightCard", arrayOf(Double::class.javaPrimitiveType!!), 120.0)
                assertTrue(activity.findViewById<TextView>(R.id.tvLightState).text.toString().contains("نامشخص"))
                assertTrue(activity.findViewById<TextView>(R.id.tvLightCountdown).text.toString().contains("چراغ واقعی"))
                assertEquals(0.22f, activity.findViewById<View>(R.id.vRed).alpha, 0.001f)
                assertEquals(0.22f, activity.findViewById<View>(R.id.vYellow).alpha, 0.001f)
                assertEquals(0.22f, activity.findViewById<View>(R.id.vGreen).alpha, 0.001f)
            }
        }
    }
}
