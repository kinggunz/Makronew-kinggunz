# Makro by Gunz

Aplikasi Android (Kotlin, native) — bukan simulasi. Fitur benar-benar berjalan lewat AccessibilityService + WindowManager overlay milik sistem Android.

## Alur aplikasi
1. **Splash screen** — logo custom + nama "Makro by Gunz" tampil ±1.5 detik lalu masuk ke menu utama.
2. **Menu utama**, urut dari atas:
   - **Status Aktivasi** (paling atas) — tombol untuk mengaktifkan Accessibility Service.
   - **Aplikasi Target** — baris horizontal berisi ikon aplikasi yang sudah dipilih. Bisa **digeser urutannya langsung** (drag & drop dengan animasi bawaan RecyclerView). Tap salah satu ikon → muncul pilihan **"Ganti aplikasi"** atau **"Hapus dari daftar"**. Di ujung kanan ada tombol **➕** untuk menambah aplikasi baru (bisa lebih dari satu aplikasi target).
   - **Atur Kecepatan Ketuk Otomatis** — membuka halaman baru dengan slider dari **Lambat** sampai **Sangat Cepat** (20ms–1000ms per ketukan), ada tombol **Konfirmasi** dan **Kembali**.
   - **Tombol Mulai** (paling bawah) — **nonaktif (abu-abu)** selama Accessibility Service belum aktif atau belum ada aplikasi dipilih. Begitu ditekan: otomatis **membuka aplikasi target pertama** dan langsung menyalakan **mode mengambang** di atasnya.

## Fitur mode mengambang (v5 — update terbaru)
- **Konsep TAHAN-UNTUK-TEKAN**: tombol sentuh cepat kini transparan dengan garis lingkaran oranye dan teks "TAHAN TEKAN" di tengah. **Selama ditahan** → ketuk-otomatis berjalan terus. **Begitu dilepas** → langsung berhenti. Border berubah lebih terang/putih saat sedang aktif sebagai indikator visual.
- **Bubble tetap TERKUNCI secara default** (tidak bisa digeser) kecuali mode **✎ Edit Posisi** dibuka dari panel tepi kiri. Saat mode edit aktif: bubble bisa digeser bebas + muncul tombol ✓ konfirmasi & ✕ matikan; begitu ✓ ditekan, langsung terkunci lagi.
- **Panel tepi kiri**: hanya garis kecil terlihat di awal (fitur tidak langsung muncul). **Digeser ke arah mana pun** (kiri atau kanan) yang cukup jauh akan **membuka/menutup** panel; otomatis tertutup lagi setelah **3 detik** tanpa disentuh. Bisa digeser naik-turun di sepanjang tepi kiri. Isi panel:
  - **⌖ Crosshair** — nyala/mati, ikon target merah yang bisa digeser bebas ke mana saja; kalau aktif, ketuk-otomatis menembak di posisi crosshair, bukan di posisi bubble.
  - **✎ Edit Posisi** — buka/tutup mode edit bubble.
  - **⤢ Ukuran** — buka slider untuk mengubah ukuran tombol sentuh cepat secara realtime, dari kecil sampai besar.
- **Layar tetap 100% bebas digerakkan** kapan saja — termasuk **saat ketuk-otomatis sedang aktif** — berkat flag `FLAG_NOT_TOUCH_MODAL` di semua jendela mengambang. Kamu bisa menahan tombol sambil menyentuh bagian layar lain secara bersamaan tanpa nyangkut.
- **Kompatibilitas**: minimum Android 7.0 (API 24) sampai versi Android terbaru — sudah ditambahkan properti `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` di manifest supaya foreground service tetap berjalan aman di Android 14 ke atas.
- **Tampilan menu utama sekarang landscape** (dua kolom: kiri status+kecepatan+tombol Mulai, kanan daftar aplikasi target), begitu juga semua halaman lain di aplikasi.
- Notifikasi mode mengambang tetap ada tombol "Matikan" langsung.

## Fitur mode mengambang (v6 — perbaikan penting)
- **Ganti dari "tahan" ke "double tap"** untuk tombol sentuh cepat. Alasan: saat ditahan, jari kamu jadi 1 sentuhan yang terus aktif berbarengan dengan ketukan sintetis ke aplikasi yang sama — inilah yang bikin layar terasa "tidak bisa disentuh yang lain". Ini keterbatasan sistem Android untuk fitur Accessibility Service, bukan bug dari kode, dan tidak bisa diperbaiki dengan izin tambahan.
- **Solusinya**: **double tap** tombol mengambang untuk **mulai**, ketukan otomatis akan **terus berjalan di background** tanpa perlu jari menempel sama sekali — jadi layar 100% bebas dipakai untuk apa saja. **Double tap lagi** untuk **berhenti**.
- Tampilan tombol tetap transparan bergaris lingkaran, sekarang teksnya "2x TAP" agar sesuai cara pakainya.
- Semua fitur lain (panel tepi kiri, crosshair, ukuran, kunci geser lewat Edit Posisi, kompatibilitas Android 7+, tampilan landscape) tetap sama seperti sebelumnya.

## Cara build APK LANGSUNG DARI HP (tanpa PC)

Build native Android (Gradle + Kotlin) tidak bisa jalan langsung di HP tanpa Android Studio/SDK. Cara paling praktis dari HP adalah **build di cloud pakai GitHub Actions** — workflow-nya sudah saya siapkan di `.github/workflows/build-apk.yml`, jadi HP kamu cuma tugasnya upload kode, sisanya di-build otomatis oleh server GitHub.

### Langkah-langkah (semua di HP)
1. **Ekstrak** ZIP `MakroByGunz.zip` ini di HP (pakai app Files/ZIP Extractor bawaan).
2. **Install Termux** (disarankan dari F-Droid, bukan Play Store karena versi Play Store sudah usang).
3. Buka Termux, jalankan:
   ```
   pkg update && pkg install git -y
   termux-setup-storage
   cd storage/downloads/MakroByGunz    # sesuaikan lokasi hasil ekstrak
   git init
   git add .
   git commit -m "init makro by gunz"
   ```
4. Buat **repository baru** di GitHub (lewat browser HP, buka github.com → New repository, kasih nama misal `makro-by-gunz`, jangan centang "add README").
5. Buat **Personal Access Token** (untuk login lewat Termux): GitHub → Settings → Developer settings → Personal access tokens → Generate new token (centang scope `repo`). Simpan tokennya.
6. Kembali ke Termux:
   ```
   git remote add origin https://github.com/USERNAME/makro-by-gunz.git
   git branch -M main
   git push -u origin main
   ```
   Saat diminta username/password: username = username GitHub kamu, password = **token** dari langkah 5 (bukan password akun).
7. Buka repo kamu di browser → tab **Actions** → akan ada proses "Build APK" berjalan otomatis (±3-5 menit).
8. Setelah selesai (centang hijau), buka run tersebut → scroll ke bawah ke bagian **Artifacts** → download `MakroByGunz-debug-apk` (berupa .zip berisi APK).
9. Ekstrak zip itu, dapat file `app-debug.apk` → install di HP (aktifkan dulu izin "Install aplikasi tidak dikenal" untuk browser/file manager kamu).

### Alternatif (kurang disarankan)
Ada app seperti **AIDE** yang bisa coding & build APK langsung di HP tanpa PC/internet berat, tapi dukungan Kotlin + Gradle modern + AccessibilityService di AIDE terbatas dan sering gagal untuk project seperti ini. GitHub Actions di atas jauh lebih reliable.

## Izin yang diminta ke pengguna (native Android, sesuai permintaan)
- **Accessibility Service** — wajib, untuk mengirim ketukan otomatis.
- **SYSTEM_ALERT_WINDOW (tampil di atas aplikasi lain)** — wajib, untuk bubble mengambang.
- **QUERY_ALL_PACKAGES** — untuk menampilkan semua aplikasi terpasang di daftar pilihan.

## Logo custom
Karena belum ada file logo darimu, saya buatkan logo custom sendiri (vector, bukan foto/dummy): lingkaran oranye dengan simbol petir putih di tengah — dipakai sebagai icon aplikasi (adaptive icon, muncul di launcher HP) maupun di splash screen. File-nya di:
- `res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background.xml` + `mipmap-anydpi-v26/ic_launcher.xml` → icon aplikasi
- `res/drawable/logo_splash.xml` → logo di splash screen

Kalau nanti kamu punya logo sendiri (file PNG/JPG), kirim ke saya dan saya gantikan file-file di atas dengan logo kamu.

## Catatan teknis
- Karena ketukan dikirim tepat di titik ikon, di beberapa kondisi Android bisa saja ketukan mengenai jendela overlay itu sendiri, bukan aplikasi di bawahnya (batasan umum pada semua auto-clicker berbasis AccessibilityService). Jika ingin titik ketuk yang terpisah dari posisi ikon (misalnya ikon di pojok, titik ketuk di tengah layar), beri tahu saya dan saya tambahkan mode "atur titik ketuk terpisah".
- Nama paket: `com.gunz.makro`. Ganti `applicationId` di `app/build.gradle` bila perlu.
