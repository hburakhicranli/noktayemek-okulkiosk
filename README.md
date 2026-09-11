# Kiosk Filo Sistemi

12 Lenovo TB305FU tableti tek bir uygulamaya kilitleyip merkezden izlemek/müdahale
etmek için kendi kiosk APK'mız + Firebase + web panel.

Ayrıntılı mimari/gereksinim dokümanı için önceki denetim çıktısına bakabilirsin;
bu repo o dokümandaki Faz 1 (MVP) planının kodu.

## Parçalar
- [`firebase/`](firebase/README.md) — Firestore kuralları, veri şeması, proje kurulumu
- [`panel/`](panel/) — yönetim paneli (React + Vite + TS), `npm install && npm run dev`
- [`android-kiosk/`](android-kiosk/README.md) — kiosk APK (Kotlin), Android Studio ile derlenir

## Kurulum sırası
1. `firebase/README.md` — Firebase projesini (Firestore + Auth) hazırla, kuralları deploy et
2. `panel/` — `.env.example`'ı `.env` yapıp Firebase Web app anahtarlarını doldur, `npm run dev`
3. `android-kiosk/README.md` — `google-services.json`'ı ekle, Android Studio ile derle,
   her tableti device owner olarak provision et
