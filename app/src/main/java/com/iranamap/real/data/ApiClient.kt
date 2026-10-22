package com.iranamap.real.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OsrmApi {
    @GET("route/v1/driving/{coords}")
    suspend fun route(
        @Path("coords") coords: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("annotations") annotations: String = "duration,distance"
    ): OsrmResponse

    @GET("nearest/v1/driving/{coords}")
    suspend fun nearest(
        @Path("coords") coords: String,
        @Query("number") number: Int = 1
    ): OsrmResponse
}

interface NominatimApi {
    @Headers("Accept-Language: fa,fa-IR;q=0.9")
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 12,
        @Query("countrycodes") countryCodes: String = "ir",
        @Query("accept-language") language: String = "fa",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("viewbox") viewBox: String? = null,
        @Query("bounded") bounded: Int = 0
    ): List<NominatimPlace>

    @Headers("Accept-Language: fa,fa-IR;q=0.9")
    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "jsonv2",
        @Query("accept-language") language: String = "fa",
        @Query("zoom") zoom: Int = 18
    ): NominatimPlace
}

interface OverpassApi {
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun query(@Field("data") data: String): OverpassResponse
}

object ApiClient {

    private const val USER_AGENT = "IranAmap/2.0 (Android; Persian navigation app; contact: iranamap.app)"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "fa-IR,fa;q=0.9")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val osrm: OsrmApi by lazy { retrofit("https://router.project-osrm.org/").create(OsrmApi::class.java) }
    val osrmBackup: OsrmApi by lazy { retrofit("https://routing.openstreetmap.de/routed-car/").create(OsrmApi::class.java) }
    val nominatim: NominatimApi by lazy { retrofit("https://nominatim.openstreetmap.org/").create(NominatimApi::class.java) }
    val overpass: OverpassApi by lazy { retrofit("https://overpass-api.de/").create(OverpassApi::class.java) }
}
