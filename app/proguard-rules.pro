# Aturan R8 untuk pembungkus WebView.

# Pertahankan method yang dipanggil dari JavaScript bila nanti ditambahkan.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Activity utama dipakai lewat manifest.
-keep class id.orangesky.app.MainActivity { *; }
