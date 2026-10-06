import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
  // Add the Crashlytics Gradle plugin
  id("com.google.firebase.crashlytics")
}


val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: "TU_API_KEY_AQUI"

android {
    namespace = "com.alkilapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alkilapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 42
        versionName = "1.60.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        create("release") {
            // Las credenciales vienen de keystore.properties (archivo local, esta en
            // .gitignore) y NO de variables de entorno: antes solo se leian las env
            // vars, asi que un build limpio fallaba con "Failed to read key from
            // store" y no habia forma de saber que faltaba. Las env vars siguen
            // teniendo prioridad para poder firmar en CI.
            val signingProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun prop(varName: String, envName: String): String =
                System.getenv(envName)?.takeIf { it.isNotBlank() }
                    ?: signingProps.getProperty(varName)?.takeIf { it.isNotBlank() }
                    ?: ""

            storeFile = file(prop("storeFile", "KEYSTORE_FILE").ifBlank { "../alkilapp-release.keystore" })
            storePassword = prop("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = prop("keyAlias", "KEY_ALIAS")
            keyPassword = prop("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // shrinkResources queda APAGADO a proposito: reduce el APK pero rompe en
            // runtime cualquier recurso que se resuelva por nombre en tiempo de
            // ejecucion (notificaciones, getIdentifier, layouts dinamicos). No se puede
            // validar en un dispositivo real todavia porque la release firmada no instala
            // sobre la app actual (firmas distintas), asi que activarlo a ciegas
            // arriesgaria un crash en produccion. Encenderlo junto con una prueba en
            // dispositivo.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
    }
}

dependencies {
    // Google Maps SDK & Location Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // Places SDK (New): autocomplete de direcciones al publicar un inmueble
    implementation("com.google.android.libraries.places:places:3.5.0")

    // Google Play Billing: compras in-app (destacados)
    implementation("com.android.billingclient:billing:8.0.0")

    // Firebase: Firestore (base de datos) + Authentication (registro de usuarios)
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // Crashlytics and Analytics (using same BoM 33.1.1)
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // Android Jetpack & Material Design 3
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Jetpack Compose (pantalla principal + listado de inmuebles)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}