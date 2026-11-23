plugins {
    // AGP 9 has BUILT-IN Kotlin support: com.android.application already registers the
    // `kotlin` extension and compiles src/main/kotlin. Applying org.jetbrains.kotlin.android
    // on top fails with "extension 'kotlin' already registered". So NO Kotlin plugin here.
    alias(libs.plugins.android.application)
    // Compose compiler plugin is required for Kotlin 2.0+ (even with AGP built-in Kotlin).
    // Version 2.2.10 must match the Kotlin compiler bundled with AGP 9.2.1.
    alias(libs.plugins.compose.compiler)
    // Hilt DI — generates @HiltAndroidApp / @AndroidEntryPoint boilerplate.
    alias(libs.plugins.hilt.android)
    // KSP — runs hilt-compiler symbol processor (preferred over kapt for Kotlin 2.x).
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nextz.kanomtts"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nextz.kanomtts"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        // SPIKE FINDING: Readium 3.1.x requires Java 17 source/target (was Java 11) and
        // core library desugaring. Bumping these is a project-wide toolchain change.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    // With AGP built-in Kotlin, the Kotlin jvmTarget defaults to targetCompatibility (17 above),
    // so no kotlinOptions/compilerOptions block is required.

    testOptions {
        unitTests {
            // Required by Robolectric to load Android resources (e.g. SQLite, manifests).
            isIncludeAndroidResources = true
            // Robolectric fetches android-all at test runtime through its own HttpURLConnection
            // client, independently of Gradle repositories. Route that fetch through the
            // Google-hosted Maven Central mirror to avoid failures from repo1.maven.org.
            all {
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2/"
                )
                it.systemProperty("robolectric.dependency.repo.id", "google-maven-central-mirror")
            }
        }
    }

    // Export Room schema files for version-controlled migration tracking.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // com.google.android.material provides the Theme.Material3.* XML window theme; appcompat is its base. Keep.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Plan 01 Thai text pipeline — reused by the probe's tokenizer adapter.
    implementation(project(":text:pipeline"))

    // jsoup — HTML/XML parser for Thai ZWSP injection in EPUB content resources (Task 6)
    implementation(libs.jsoup)

    // Plan 04 replacement engine domain type
    implementation(project(":text:replacement"))

    // Readium — reader foundation under evaluation by this spike.
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)

    // media3 — Jetpack Media3 for background playback
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    // Jetpack Compose (BOM 2026.06.00 → Material 3 1.4.0 Expressive)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // material-icons-core is included via BOM (no version needed); provides Filled icons used
    // by KanomDestinations (List, PlayArrow, Settings). Extended set NOT needed — avoids ~4 MB.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room (KSP-based ORM)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (preferences/settings persistence)
    implementation(libs.androidx.datastore.preferences)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Jetpack Navigation for Compose (not in BOM — explicit version in catalog)
    implementation(libs.androidx.navigation.compose)

    // Plan 05 library-import deps
    // Coil 3 — async image loading in Compose (cover art thumbnails)
    implementation(libs.coil.compose)
    // hiltViewModel() Compose integration
    implementation(libs.androidx.hilt.navigation.compose)
    // viewModel() delegate for Compose screens
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    // Turbine — coroutine Flow testing (Plan 05+)
    testImplementation(libs.turbine)
    // Robolectric-based Room unit tests (JVM, no device required)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
