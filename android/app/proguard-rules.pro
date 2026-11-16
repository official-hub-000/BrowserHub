# Keep BrowserHub JNI / WebView @JavascriptInterface bridge methods intact under R8
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-keepclassmembers class com.browserhub.app.bridge.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.browserhub.app.bridge.BrowserHubJavascriptBridge { *; }

# Keep Android VpnService & MainActivity entry points
-keep class com.browserhub.app.MainActivity { *; }
-keep class com.browserhub.app.vpn.HubVpnService { *; }

# Mozilla GeckoView & WebExtensions JNI reflection guards
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# WireGuard Tunnel & ONNX Runtime JNI guards
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Strip verbose debug logging in Play Store release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
