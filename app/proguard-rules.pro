# ProGuard rules for AlkilApp

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Billing classes
-keep class com.android.billingclient.** { *; }

# Keep Places SDK classes
-keep class com.google.android.libraries.places.** { *; }

# Keep ViewBinding classes
-keep class com.alkilapp.databinding.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep application classes
-keep class com.alkilapp.** { *; }

# Keep R8/ProGuard from removing unused enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable implementations
-keep class * implements java.io.Serializable { *; }

# Don't warn about missing classes
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-dontwarn com.android.billingclient.**
-dontwarn com.google.android.libraries.places.**

# Conservar modelos de datos del paquete de la app
-keep class com.alkilapp.data.** { *; }
-keep class com.alkilapp.model.** { *; }
-keepclassmembers class com.alkilapp.data.** { *; }
-keepclassmembers class com.alkilapp.model.** { *; }

# Mantener anotaciones y serialización de Firebase Firestore
-keepattributes *Annotation*, Signature, InnerClasses
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}