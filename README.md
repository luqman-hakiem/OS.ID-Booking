# ORANGESKY.ID — APK ringan untuk orangesky.id

Pembungkus WebView untuk https://orangesky.id/ yang dibuat seringan mungkin: tanpa
AppCompat, tanpa library splash, APK di-minify R8, dan WebView dioptimalkan supaya
cold start terasa seperti membuka website langsung di browser.

## Tangkapan layar

<p align="center">
  <img src="docs/home-orangsky.jpg" width="320" alt="Tangkapan layar Home ORANGESKY.ID">
</p>

## Download

Ambil APK terbaru di halaman **Releases**:

**https://github.com/luqman-hakiem/orangesky-app/releases/latest**

File: `OrangeSky.id 1.3.apk` (Android 8.0 ke atas).

Download langsung:
**https://github.com/luqman-hakiem/orangesky-app/releases/latest/download/OrangeSky.id.1.3.apk**

Cara pasang:

1. Download APK di HP Android.
2. Buka file APK, lalu izinkan **Install unknown apps** untuk browser/file manager
   yang dipakai.
3. Setelah terpasang, buka **ORANGESKY.ID**.

> APK rilis ini ditandatangani dengan debug key (belum ke Play Store). Kalau sebelumnya
> sudah memasang build lama, uninstall dulu karena tanda tangannya bisa berbeda.

## Yang membuat APK ringan

- **Tanpa AppCompat & library splashscreen** — hanya `activity-ktx` + `SwipeRefreshLayout`.
  Startup lebih cepat, dependensi dan ukuran APK lebih kecil.
- **Splash bawaan** — Android 12+ memakai splash sistem; Android lama memakai
  `windowBackground` (navy + wordmark) yang langsung tampil tanpa delay.
- **R8 + shrinkResources** di build release — kode & resource tak terpakai dibuang.
- **WebView hemat** — autoplay video dimatikan, timer/animasi di-pause saat app di
  belakang, cache browser dipakai, file/content access dimatikan.
- **Renderer crash tidak bikin app mati** — WebView dibuat ulang otomatis.
- **Link `target="_blank"`** tetap terbuka di dalam app.
- **Ikut ukuran layar & ukuran font sistem** — konten memakai viewport situs, bisa
  cubit (pinch) untuk zoom, dan teks ikut Setelan > Tampilan > Ukuran font.
- Pull-to-refresh, tombol back, halaman offline, upload file, dan link
  WA/IG/YouTube/tel/mailto tetap jalan seperti sebelumnya.

## Tampilan ala aplikasi

App punya **bottom navigation** sendiri di bawah WebView, seperti desain `utama.png`:

| Tab | Tujuan |
| --- | --- |
| Home | `https://orangesky.id/` |
| Voucher | `https://orangesky.id/?menu=voucher` |
| Contact Us | `https://orangesky.id/?menu=contactUs` |
| My Booking | `https://orangesky.id/login/?from=mybooking` (area akun; daftar booking muncul setelah login) |
| Account | `https://orangesky.id/login/` |

- Dibuat dari `LinearLayout` + ikon vektor 24dp + label 11sp, tinggi ±55dp, tanpa
  library navigasi tambahan (tetap ringan, tidak menambah ukuran APK berarti).
- Tab aktif mengikuti URL (gold `#C0A751`, sisanya abu-abu), jadi tetap benar
  setelah tombol back atau membuka tautan dari dalam situs. My Booking dan Account
  sama-sama membuka area login; app membedakannya lewat parameter `from=mybooking`.
- Bottom bar otomatis hilang saat konten di-scroll ke bawah dan muncul lagi saat
  di-scroll ke atas (atau saat halaman kembali ke posisi paling atas).
- Menu di header situs (Home/Voucher/Contact/Currency/Login) disembunyikan supaya
  tidak dobel dengan bottom bar; navigasi sepenuhnya lewat bottom bar app.
- Tombol panah ke atas bawaan situs di kanan bawah juga disembunyikan.
- Home ditekan saat sudah di beranda = gulir ke atas (tidak reload).
- Isi halaman tetap 100% situs orangesky.id; yang ditambahkan app hanya bilah bawah ini.

## Ukuran layar, kepadatan, dan font

App ini hanya membungkus situs, jadi tata letak utama mengikuti CSS responsif
orangesky.id (`<meta name="viewport" content="width=device-width, initial-scale=1.0">`).
Yang diatur di sisi app:

- `useWideViewPort` + `loadWithOverviewMode` — WebView memakai lebar layar sebagai
  lebar layout, lalu situs memilih breakpoint-nya sendiri (Bootstrap: 576/768/992/1200 px).
- `textZoom` diambil dari `Configuration.fontScale` (dibatasi 85–200%), jadi pengaturan
  **Ukuran font** Android ikut terpakai. Perubahan font ditangani lewat
  `onConfigurationChanged` tanpa reload halaman.
- `configChanges` memuat `orientation|screenSize|smallestScreenSize|screenLayout|fontScale`,
  jadi rotasi, split-screen, dan lipat/buka foldable tidak memuat ulang halaman.
- `resizeableActivity=true` — siap untuk multi-window dan layar besar.
- Semua tampilan app (spinner, splash, status bar) memakai satuan dp/warna, bukan
  ukuran piksel tetap, sehingga menyesuaikan kepadatan layar.
- Pinch-to-zoom aktif (tombol zoom disembunyikan) untuk pengguna yang butuh teks lebih besar.

Sudah diuji dengan emulasi lebar layar 320–1280 dp (termasuk landscape 844×390):
tidak ada overflow horizontal (`scrollWidth == viewport`) dan navbar berubah jadi menu
hamburger di bawah 992 px.

## Build sendiri

Build otomatis lewat GitHub Actions (tanpa Android Studio):

1. Buka tab **Actions > Build APK > Run workflow** (atau push ke `main`).
2. Download artifact **orangesky-apk**. Isinya:
   - `release/app-release.apk` — **pakai ini**, sudah di-minify (paling kecil & cepat).
   - `debug/app-debug.apk` — untuk uji coba/debug.

## APK release bertanda tangan sendiri (untuk dibagikan / Play Store)

Build release tanpa keystore otomatis ditandatangani dengan debug key, jadi cukup
untuk dibagikan langsung tetapi **tidak bisa update** kalau key berubah dan tidak bisa
dipakai ke Play Store.

1. Buat keystore SEKALI saja (simpan file + password di tempat aman):
   ```
   keytool -genkeypair -v -keystore orangesky.jks -alias orangesky -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Ubah keystore jadi teks base64:
   - Mac/Linux: `base64 -w0 orangesky.jks`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("orangesky.jks"))`
3. Di GitHub: **Settings > Secrets and variables > Actions > New repository secret**,
   isi 4 secret: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (contoh: orangesky),
   `KEY_PASSWORD`.
4. Jalankan **Actions > Build APK** lagi. Release APK akan ditandatangani dengan
   keystore tersebut.

Jangan upload file `.jks` ke repo.

## Catatan

- Ganti URL di `MainActivity.kt` (`homeUrl`) kalau domain berubah.
- Naikkan `versionCode` di `app/build.gradle.kts` setiap rilis update.
- Ikon & splash memakai wordmark resmi orangesky.id (`logo_orangeskyid.png`, gold di
  navy). Foreground ikon berupa WebP lossless satu densitas (xxxhdpi) supaya APK tetap
  kecil (~11 KB); Android menyesuaikan sendiri untuk layar lain.
