import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firebase: solo se activa cuando pones app/google-services.json (ver README)
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) apply(plugin = "com.google.gms.google-services")

// Firma: los datos están en keystore.properties (fuera de git). Guarda una copia de ese archivo y del .jks.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// En GitHub Actions no hay keystore.properties: los mismos datos llegan como variables de entorno (ver .github/FIRMA_APK.md)
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

android {
    namespace = "com.daniel.reproductor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adrian.minu"
        minSdk = 26
        targetSdk = 36
        // GitHub Actions las calcula del tag (v1.2 -> 10200 / "1.2"); en local se usan estos valores
        versionCode = providers.gradleProperty("minu.versionCode").map(String::toInt).getOrElse(2)
        versionName = providers.gradleProperty("minu.versionName").getOrElse("1.0")
        // URL del servidor en Render: se cambia en gradle.properties (minu.serverUrl)
        buildConfigField("String", "SERVER_URL", "\"${providers.gradleProperty("minu.serverUrl").getOrElse("")}\"")
        // "ID de cliente web" de Google para "Continuar con Google" (gradle.properties: minu.googleWebClientId)
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("minu.googleWebClientId").getOrElse("")}\"")
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
        buildConfigField("String", "NEWPIPE_VERSION", "\"${libs.versions.newpipeExtractor.get()}\"")
    }

    signingConfigs {
        val storePath = signingValue("MINU_KEYSTORE_FILE", "storeFile")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("MINU_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("MINU_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("MINU_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Mismo paquete que la firmada para que Firebase y Google la reconozcan
        }
    }

    compileOptions {
        // NewPipe Extractor usa APIs modernas de Java (java.time, etc.)
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.newpipe.extractor)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.reorderable)
    implementation(libs.palette.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // org.json real para las pruebas en la JVM

    // Capturas de pantalla de los estilos en la JVM (./gradlew testDebugUnitTest -Pscreenshots)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // Las capturas solo se generan cuando se piden, para que las pruebas normales sigan siendo rápidas
    val screenshots = project.hasProperty("screenshots")
    systemProperty("roborazzi.test.record", (screenshots || project.hasProperty("cloud")).toString())
    if (!screenshots) exclude("**/StyleScreenshots*")
    // La prueba de punta a punta necesita el servidor local (server/test/dev-server.js)
    if (!project.hasProperty("cloud")) exclude("**/CloudIntegrationTest*")
    maxHeapSize = "3g"
}
