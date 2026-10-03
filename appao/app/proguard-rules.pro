# WebView JavaScript bridge is invoked through reflection.
-keepattributes *Annotation*
-keepclassmembers class com.appao.AppViewerActivity$HtmlThemeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.appao.AppAoApp { *; }
