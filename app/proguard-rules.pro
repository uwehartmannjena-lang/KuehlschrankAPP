-keep class com.example.myapplication.data.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class io.ktor.** { *; }
-keep class retrofit2.** { *; }
-keep class com.android.billingclient.** { *; }

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn java.lang.management.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn io.ktor.**

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
