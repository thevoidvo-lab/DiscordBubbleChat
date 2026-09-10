# Discord Bubble Chat

App overlay bubble buat kirim & terima pesan Discord sambil main game, tanpa buka app Discord.

## Cara pakai (semua dari HP, gak perlu laptop)

### 1. Bikin Bot Discord
1. Buka https://discord.com/developers/applications dari browser HP
2. **New Application** → kasih nama bebas
3. Masuk tab **Bot** (sidebar kiri) → **Add Bot**
4. Di halaman Bot, scroll ke **Privileged Gateway Intents** → aktifin **MESSAGE CONTENT INTENT** (wajib, biar bot bisa baca isi pesan)
5. Klik **Reset Token** → copy token-nya, simpan baik-baik (jangan disebar, ini kayak password)
6. Masuk tab **OAuth2 → URL Generator** → centang scope **bot** → di permission centang **Send Messages** dan **Read Message History** → copy link yang muncul di bawah, buka linknya, pilih server kamu, invite bot-nya

### 2. Ambil Channel ID
1. Di app Discord: Settings → Advanced → aktifin **Developer Mode**
2. Klik kanan (atau tekan lama di HP) channel yang mau dipantau → **Copy Channel ID**

### 3. Compile APK pake GitHub Actions (gratis, dari HP)
1. Bikin akun GitHub kalau belum punya (github.com)
2. Bikin repository baru (**New repository**), kasih nama bebas, jangan dicentang "Add README"
3. Upload semua file/folder project ini ke repo itu (dari HP: buka repo → **Add file → Upload files** → drag semua isi folder project). Pastikan struktur foldernya tetap sama (folder `app/`, `.github/`, file `build.gradle`, dll di root repo)
4. Setelah upload selesai, masuk tab **Actions** di repo kamu → workflow **"Build APK"** bakal otomatis jalan (atau klik **Run workflow** manual kalau belum jalan)
5. Tunggu 3-5 menit sampai muncul centang hijau
6. Klik run yang selesai itu → scroll ke bawah ke bagian **Artifacts** → download **discord-bubble-chat-apk** (isinya app-debug.apk)

### 4. Install di HP
1. Extract file zip yang didownload tadi, dapet `app-debug.apk`
2. Settings HP → izinkan **Install from unknown sources** buat browser/file manager
3. Buka file APK-nya → Install

### 5. Setup di app
1. Buka app **Discord Bubble Chat**
2. Paste **Bot Token** dan **Channel ID** yang tadi disiapin
3. Tap **"1. Izinkan Overlay"** → di halaman settings yang muncul, aktifin izinnya buat app ini
4. Balik ke app, tap **"2. Mulai Bubble Chat"**
5. Bubble kecil bakal muncul di layar — tarik ke mana aja, tap buat buka/tutup chat panel

## Catatan
- Bot token itu rahasia — jangan pernah upload token asli ke repo GitHub publik. Isi token-nya cuma di dalam app, bukan di source code.
- Kalau repo GitHub kamu publik, orang lain bisa liat source code-nya (gak masalah, gak ada rahasia di source code), tapi kalau mau lebih aman bikin repo **Private** aja (GitHub Actions tetap jalan gratis di repo private untuk personal account).
- App ini connect ke Discord Gateway pake WebSocket, jalan di foreground service biar tetap hidup pas app lain dibuka.