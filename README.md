# MiyukiTV 🌸

<p align="center">
  <img src="https://img.shields.io/badge/Android-5.0%2B-pink?style=flat-square&logo=android"/>
  <img src="https://img.shields.io/badge/DRM-ClearKey%20%26%20Widevine-purple?style=flat-square"/>
  <img src="https://img.shields.io/badge/License-MIT-red?style=flat-square"/>
  <img src="https://img.shields.io/github/workflow/status/aurorasekai15-hub/SymphogearTV/Build%20MiyukiTV%20APK?style=flat-square"/>
</p>

<p align="center">
  Aplikasi TV streaming premium bertema anime dengan dukungan DASH + DRM ClearKey & Widevine.
</p>

---

## ✨ Fitur

- 🎌 **Tema Anime** — UI dark sakura pink/purple premium
- 📺 **Sidebar Kategori** — Nasional, Berita, Hiburan, Olahraga, Internasional, Jepang, Vision+ DRM, IndiHome DRM, Custom
- 🔐 **DASH + DRM** — Support ClearKey & Widevine penuh
- 📡 **channels.json** — Channel dikelola dari GitHub, upload channel baru cukup edit file ini
- ⭐ **Favorit** — Long-press channel untuk tambah/hapus favorit
- 🔍 **Pencarian** — Cari channel realtime
- 📱 **PiP** — Picture-in-Picture mode
- 🖥️ **TV Box** — D-pad navigation, Android TV / Leanback support
- 🔄 **Auto Update** — Cek update APK otomatis dari GitHub Releases
- 🌏 **Multi Bahasa** — Indonesia, English, 日本語, Melayu

---

## 📺 Cara Tambah Channel Baru

Edit file `channels.json` di root repo ini. Format:

```json
{
  "channels": [
    {
      "id": 1,
      "name": "Nama Channel",
      "cat": "nasional",
      "url": "https://stream-url.m3u8",
      "logo": "https://logo-url.png",
      "drm": false
    },
    {
      "id": 100,
      "name": "Channel DRM ClearKey",
      "cat": "vision",
      "url": "https://dash-stream.mpd",
      "logo": "",
      "drm": true,
      "drmType": "ClearKey",
      "licUrl": "keyid_hex:key_hex"
    },
    {
      "id": 101,
      "name": "Channel DRM Widevine",
      "cat": "indihome",
      "url": "https://dash-stream.mpd",
      "logo": "",
      "drm": true,
      "drmType": "Widevine",
      "licUrl": "https://license-server.com/widevine"
    }
  ]
}
```

### Kategori yang tersedia (`cat`):
| Key | Tampil |
|-----|--------|
| `nasional` | Nasional |
| `berita` | Berita |
| `hiburan` | Hiburan |
| `olahraga` | Olahraga |
| `internasional` | Internasional |
| `jepang` | Jepang |
| `vision` | Vision+ DRM |
| `indihome` | IndiHome DRM |
| `custom` | Custom |

---

## 🔨 Build dari Source

### Prasyarat
- Android Studio Bumblebee atau lebih baru
- JDK 17
- Android SDK 30

### Clone & Build
```bash
git clone https://github.com/aurorasekai15-hub/SymphogearTV.git
cd SymphogearTV
chmod +x gradlew
./gradlew assembleRelease
```

### GitHub Actions (Otomatis)
Push ke branch `main` → APK otomatis ter-build dan ter-upload ke GitHub Releases.

---

## 📦 Download APK

Lihat di tab [Releases](../../releases) untuk download APK terbaru.

---

## 🗂️ Struktur Project

```
MiyukiTV/
├── .github/workflows/build.yml   ← GitHub Actions auto-build
├── channels.json                  ← ⬅️ EDIT INI untuk tambah channel
├── json/release.json              ← Info versi untuk auto-update
├── app/
│   └── src/main/
│       ├── java/com/miyuki/tv/
│       │   ├── MainActivity.kt
│       │   ├── PlayerActivity.kt   ← ExoPlayer DASH+DRM
│       │   ├── SplashActivity.kt
│       │   ├── adapter/
│       │   ├── dialog/
│       │   ├── extension/
│       │   ├── extra/
│       │   └── model/
│       └── res/                   ← Anime theme resources
└── gradle/
```

---

## 📝 Lisensi

MIT License — bebas digunakan dan dimodifikasi.

---

<p align="center">Made with ♥ and 🌸 anime spirit</p>
