# The native fog layer binds its JNI functions by the class and method names of NativeFog.
-keep class com.onezee.triptrack.map.fog.NativeFog { native <methods>; }
# MapLibreAbi reads MapLibre's version by reflection, so the loaded one is checked, not an inlined copy.
-keepclassmembers class org.maplibre.android.BuildConfig {
    public static final java.lang.String MAPLIBRE_VERSION_STRING;
    public static final java.lang.String FLAVOR;
}
