# Panduan unggah ke Google Play

Paket ini sudah memenuhi syarat teknis Play per November 2026:

- **AAB bertanda tangan** (bukan APK) → `OrangeSky.id 1.6.2.aab`
- `versionCode` **17**, `versionName` **1.6.2**
- `targetSdk` **36** (Android 16) — wajib untuk aplikasi baru sejak 31 Agustus 2026
- `minSdk` **26** (Android 8.0), hanya izin INTERNET, tanpa iklan/analytics
- Sudah menangani edge-to-edge Android 15/16 (status bar navy, navigation bar putih)

## 1. Yang harus disiapkan sekali

1. **Akun Google Play Console** — daftar di
   https://play.google.com/console, biaya **US$25 sekali bayar**, lalu verifikasi
   identitas (KTP + alamat, biasanya 1–3 hari).
2. **Akun developer personal baru?** Google mewajibkan **closed testing dengan
   minimal 12 penguji selama 14 hari** sebelum bisa mengajukan produksi. Siapkan
   12 email Gmail penguji lebih awal.
3. **Kunci tanda tangan** — `orangesky-release.jks` + password di
   `orangesky-release-info.txt`. Simpan 2 salinan (cloud + flashdisk).

## 2. Buat aplikasi di Play Console

1. **Create app** → nama `ORANGESKY.ID — Booking Hotel`, bahasa default
   **Indonesia**, jenis **App**, gratis.
2. Isi deklarasi: bukan game, tidak punya kebijakan privasi khusus anak, dll.

## 3. App signing — PENTING, pilih kunci yang sudah ada

APK GitHub (1.4, 1.5, 1.6, 1.6.1, 1.6.2, seterusnya) ditandatangani dengan `orangesky-release.jks`.
Kalau Play memakai kunci buatan Google, tanda tangannya berbeda dan pengguna
tidak bisa berpindah antara APK GitHub dan versi Play (muncul "App not installed").

Di **Play Console → Test and release → Setup → App signing**:

1. Pilih **"Use existing app signing key"** (bukan "Let Google generate key").
2. Ikuti perintah ekspor yang ditampilkan Console (alat `pepk.jar`):
   - keystore: `orangesky-release.jks`
   - alias: `orangesky`
   - password: lihat `orangesky-release-info.txt`
3. Unggah berkas hasil ekspor. Google menyimpan kunci itu dan memakai tanda
   tangan yang sama dengan APK GitHub.

Dengan begitu, update dari GitHub maupun Play tetap kompatibel.

## 4. Unggah AAB

1. **Test and release → Internal testing → Create new release**.
2. Unggah `C:\Users\PC Kentang\Documents\Default Project\OrangeSky.id 1.6.2.aab`
   (251.243 byte, SHA-256 `CE9D5F56435B71409D2237F4DD460D0243692456ED2723841CDB9DA0D311DBC5`).
3. Catatan rilis (contoh):

   ```
   Rilis pertama di Google Play.
   - Navigasi bawah: Beranda, Voucher, Bantuan, Booking, Akun
   - Bottom bar pill melayang, getar halus, dan posisi scroll tiap tab diingat
   - Voucher: cari, urutkan, S&K dilipat, muat lebih banyak, akses Merchant
   - Booking hotel dan klaim voucher langsung dari aplikasi
   - Ringan, tanpa iklan, hanya izin internet
   - Perbaikan tampilan edge-to-edge Android 15/16
   ```

4. Tambahkan diri sendiri sebagai internal tester, pasang dari link internal,
   lalu cek: status bar navy, bottom bar tidak tertutup gesture bar, keyboard
   tidak menutupi kolom input, login/booking berjalan.
5. Setelah lolos, naikkan ke **Closed testing** (12 penguji / 14 hari untuk akun
   personal), lalu **Production**.

## 5. Aset toko (folder `store/`)

| Berkas | Dipakai di |
|---|---|
| `icon-512.png` | App icon (512×512) |
| `feature-graphic.jpg` | Feature graphic (1024×500) |
| `phone-1-home.jpg` | Screenshot HP 1 (1080×2160) |
| `phone-2-fitur.jpg` | Screenshot HP 2 (1080×1920) |
| `tablet-7-home.jpg` | Screenshot tablet 7" (1200×1920) |
| `tablet-10-home.jpg` | Screenshot tablet 10" (1600×2560) |
| `listing-id.md` | Judul, deskripsi singkat, deskripsi lengkap (ID) |
| `listing-en.md` | Terjemahan Inggris |
| `data-safety.md` | Jawaban formulir Data safety |
| `privacy-policy.html` | Sumber kebijakan privasi (disalin ke `docs/`) |

URL kebijakan privasi yang dipakai di Play Console:

```
https://luqman-hakiem.github.io/orangesky-app/privacy.html
```

## 6. Formulir lain di Play Console

- **Privacy policy:** isi URL di atas.
- **App access:** jelaskan bahwa Beranda/Voucher/Bantuan terbuka tanpa login;
  Booking/Akun butuh akun. Buat akun demo untuk reviewer (lihat
  `data-safety.md`).
- **Ads:** No ads.
- **Content rating:** isi kuesioner IARC; kategori Travel → biasanya **Everyone /
  PEGI 3 / 3+**.
- **Target audience:** pilih **18+** (ada transaksi pemesanan), bukan aplikasi
  anak.
- **Data safety:** ikuti `data-safety.md`.
- **News app / COVID / government app:** No.
- **Negara distribusi:** Indonesia (bisa ditambah), harga gratis.

## 7. Update berikutnya (1.6.2, 1.7, …)

1. Ubah `versionCode` dan `versionName` di
   `app/build.gradle.kts` (versionCode **harus selalu naik**).
2. Ubah UA di `MainActivity.kt` (`OrangeSkyApp/1.6.2`).
3. Commit + push → GitHub Actions membangun APK dan AAB.
4. Unduh artifact **orangesky-aab**, unggah ke Play Console.
5. Unduh artifact **orangesky-apk**, ganti aset rilis GitHub seperti biasa.

Keduanya memakai kunci yang sama, jadi pengguna Play dan GitHub sama-sama bisa
update tanpa uninstall.

## 8. Kalau ditolak "minimum functionality"

Aplikasi pembungkus situs bisa ditinjau manual. Kalau muncul penolakan:

- Jelaskan bahwa ini aplikasi resmi brand, bukan sekadar bookmark: ada bottom
  navigation native, halaman offline, integrasi login/booking, dan pull-to-refresh.
- Pastikan kebijakan privasi dapat diakses publik.
- Perbaiki temuan yang disebutkan, lalu ajukan banding lewat Play Console.

## 9. Yang tidak boleh diubah setelah rilis pertama

- **Package name** `id.orangesky.app` — permanen.
- **Kunci tanda tangan** — kalau hilang, aplikasi tidak bisa di-update lagi.
