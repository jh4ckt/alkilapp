# Configuración básica de ProGuard/R8 para AlkilApp

# Mantener clases de Firebase
-keep class com.google.firebase.** { *; }

# Mantener clases de Google Play Billing
-keep class com.android.billingclient.** { *; }

# Mantener clases de Google Maps
-keep class com.google.android.gms.maps.** { *; }

# Mantener clases de Places SDK
-keep class com.google.android.libraries.places.** { *; }

# Mantener clases de Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }

# Mantener data classes y modelos
-keep class com.alkilapp.data.** { *; }

# Mantener binding views
-keep class com.alkilapp.databinding.** { *; }

# Evitar ofuscar nombres de recursos
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Mantener anotaciones de Room si se usa
-keep class androidx.room.** { *; }

# Reglas para serialización JSON (Gson/Moshi si se usa)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod