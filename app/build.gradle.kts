import java.io.File
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// نام بسته از خط فرمان هم قابل تغییر است:
//   ./gradlew assembleDebug -PappId=ir.shoma.naghshe
// اگر تعیین نشود، مقدار پیش‌فرض استفاده می‌شود.
val appId: String = (project.findProperty("appId") as String?) ?: "com.iranamap.real"
// ساخت جداگانه‌ی x86_64 فقط برای تست شبیه‌ساز؛ APK معمولی همچنان مخصوص گوشی ARM است.
val emulatorBuild = providers.gradleProperty("emulatorBuild").orNull == "true"

android {
    // namespace همان فضای نام کد است و معمولاً ثابت می‌ماند
    namespace = "com.iranamap.real"
    compileSdk = 34

    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 34
        versionCode = 33
        versionName = "3.3.0-fa"
        resourceConfigurations += listOf("fa")
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += if (emulatorBuild) listOf("x86_64") else listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // بهینه‌سازی برای دستگاه کم‌رم (Redmi 10C): کاهش حجم DEX و native libs
    packaging {
        jniLibs {
            excludes += setOf("**/libonnxruntime.so.debug", "**/*.dbg.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    androidResources {
        noCompress += listOf("onnx", "bin")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation(files("libs/sherpa-onnx.aar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// ---- sherpa-onnx (موتور گفتار کاملاً آفلاین) ----
val sherpaVersion = "1.13.8"
val libsDir = File(projectDir, "libs")
val sherpaAar = File(libsDir, "sherpa-onnx.aar")

val fetchSherpaOnnx by tasks.registering {
    description = "دریافت AAR موتور sherpa-onnx برای گفتار آفلاین فارسی"
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() }
    doLast {
        libsDir.mkdirs()
        val urls = listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar",
            "https://sourceforge.net/projects/sherpa-onnx.mirror/files/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar/download"
        )
        var ok = false
        for (u in urls) {
            try {
                println("[IranAmap] دریافت sherpa-onnx از $u")
                URI(u).toURL().openStream().use { input ->
                    sherpaAar.outputStream().use { out -> input.copyTo(out) }
                }
                if (sherpaAar.length() > 1_000_000) {
                    ok = true
                    break
                }
            } catch (e: Exception) {
                println("[IranAmap] ناموفق: ${e.message}")
            }
        }
        if (!ok) {
            throw GradleException(
                "دریافت sherpa-onnx ناموفق بود. فایل sherpa-onnx-$sherpaVersion.aar را در app/libs/ بگذارید."
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchSherpaOnnx) }
