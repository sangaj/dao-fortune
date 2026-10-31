# Keep model / engine classes (pure logic, reflection-free is fine) but preserve names used by Play Billing.
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# Keep enum valueOf / values used by kotlinx.serialization style maps if present
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
