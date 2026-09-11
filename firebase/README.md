# Firebase kurulumu

Proje zaten oluşturuldu: **KioskLauncher** (Spark plan, ücretsiz kota bu ölçek için yeterli).

## 1. Firestore'u etkinleştir
Firebase konsolunda **Build → Firestore Database → Create database** (production
mode, bölge olarak `eur3` ya da sana yakın bir bölge).

## 2. Authentication'ı etkinleştir
**Build → Authentication → Sign-in method** altında iki yöntemi aç:
- **E-posta/Şifre** — panele giriş için. Admin hesabını (`yemeknokta@gmail.com`)
  **Users** sekmesinden elle ekle — bu e-posta `firestore.rules`'daki `isAdmin()`
  kontrolüyle birebir eşleşmeli, değiştirirsen ikisini birlikte güncelle.
- **Anonymous** — tabletlerin kimlik doğrulaması için.

## 3. Web app kaydı (panel için)
**Project settings → Your apps → Web (`</>`)** ile bir web app ekle, ismi
`panel` olabilir. Firebase Hosting'i şimdilik atlayabilirsin. Sana verilen
`firebaseConfig` nesnesindeki değerleri `panel/.env` dosyasına koyacağız.

## 4. Android app kaydı (kiosk APK için)
**Project settings → Your apps → Android**. Paket adı: `com.bizim.kiosk`
(değiştirmek istersen haber ver). İndirilen `google-services.json` dosyasını
`android-kiosk/app/google-services.json` konumuna koy (bu dosya gizli
anahtarlar içerir, git'e eklenmez — `.gitignore`'da zaten hariç tutuldu).

## 5. Kuralları deploy et
```
npm install -g firebase-tools   # bir kere
firebase login                  # tarayıcı açar, Google hesabınla giriş yap
cd firebase
firebase use --add              # KioskLauncher projesini seç
firebase deploy --only firestore
```

Bu komut `firestore.rules` ve `firestore.indexes.json`'ı canlıya alır.
