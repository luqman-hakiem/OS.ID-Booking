# Data safety — jawaban yang disarankan

Isian ini mengikuti fitur login, pemesanan, pembayaran, dan unggah bukti bayar di
orangesky.id. Cocokkan dengan kebijakan privasi sebelum submit.

## Bagian "Data collection and security"

| Pertanyaan | Jawaban |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data be deleted? | **Yes** → `https://luqman-hakiem.github.io/osid/privacy.html` (atau email di halaman itu) |

## Data types

Centang hanya yang benar-benar dipakai. Semua data di bawah dikirim ke server
orangesky.id, bukan disimpan di perangkat.

### Personal info

| Data | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Name | Yes | Yes (hotel mitra) | No (wajib saat booking/daftar) | App functionality, Account management |
| Email address | Yes | Yes (hotel mitra) | No | App functionality, Account management |
| Phone number | Yes | Yes (hotel mitra) | No | App functionality, Account management |
| User IDs | Yes | No | No | Account management |

### Financial info

| Data | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Purchase history | Yes | No | No | App functionality |

Catatan: aplikasi tidak menerima nomor kartu; pembayaran diproses halaman
payment gateway di dalam situs.

### Photos and videos

| Data | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Photos | Yes | No | Yes | App functionality (unggah bukti pembayaran) |

### App activity

| Data | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| In-app search history | Yes | No | Yes | App functionality |

## Yang TIDAK perlu dicentang

Location, Contacts, Calendar, SMS/Call logs, Microphone, Health, Fitness,
Messages, Files and docs, Web browsing history, Device or other IDs, Crash logs,
Diagnostics, Installed apps, Music/audio.

Jika nanti ditambahkan analytics, crash reporting, atau push notification,
jawaban ini harus diperbarui.

## App access (kredensial reviewer)

- Sebagian besar konten terbuka tanpa login.
- **My Booking** dan **Account** memerlukan akun.
- Buat satu akun demo khusus reviewer, lalu isi di Play Console:
  `Username/email + password`, dan tulis: "Login only needed for My Booking and
  Account; Home, Voucher, and Contact Us are open."
- Jangan pakai akun pribadi atau akun staf.
