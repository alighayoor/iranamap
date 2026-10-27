package com.iranamap.real

import android.Manifest
import android.content.Intent
import android.location.Location
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.recyclerview.widget.RecyclerView
import com.iranamap.real.core.GeoPoint
import com.iranamap.real.data.OsrmApi
import com.iranamap.real.data.OsrmGeometry
import com.iranamap.real.data.OsrmLeg
import com.iranamap.real.data.OsrmManeuver
import com.iranamap.real.data.OsrmResponse
import com.iranamap.real.data.OsrmRoute
import com.iranamap.real.data.OsrmStep
import com.iranamap.real.data.RouteRepository
import com.iranamap.real.ui.CityDownloadActivity
import com.iranamap.real.ui.SearchActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun chooseDestination(activity: MapActivity) {
        invoke(activity, "setDestination", arrayOf(GeoPoint::class.java, String::class.java, String::class.java), destination, "مقصد آزمایشی", "شیراز")
    }

    private fun installFakeRouteService(activity: MapActivity) {
        val fake = FakeOsrm()
        setField(activity, "routeRepo", RouteRepository(fake, fake))
    }

    @Test
    fun searchScreenKeepsTextSentFromMapAndClearsIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_TITLE, "شیراز")
        ActivityScenario.launch<SearchActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
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
    fun offlineCityScreenOpensAndFiltersCities() {
        ActivityScenario.launch(CityDownloadActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
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
