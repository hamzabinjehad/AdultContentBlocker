# Hisn release shrinking. R8 removes unused code (most of material-icons-extended) and
# unused resources; nothing is renamed, so crash traces and diagnostics stay readable
# without a mapping file.
-dontobfuscate

# TensorFlow Lite's JNI layer calls back into these Java classes by name.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# The visual engine's WebView calls HisnResult.result(id, answer) from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
