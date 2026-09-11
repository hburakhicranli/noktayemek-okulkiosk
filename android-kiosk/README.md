# Kiosk APK — kurulum ve derleme

## Durum
Filodaki ilk tablette (Lenovo TB305FU) uçtan uca test edildi: device owner
ataması, uygulama kilitleme, web/link kilitleme (tam ekran WebView), panelden
uzaktan kilit aç/kilitle/yeniden başlat/mesaj gönder, yerel PIN ile geçici
kilit açma, ekran koruyucu + NFC ile uyandırma.

## 1. Firebase bağlantısını tamamla
`../firebase/README.md`'deki adımları yap: Firestore + Authentication'ı aç,
bir Android app kaydet (paket adı **com.bizim.kiosk**), inen
`google-services.json` dosyasını tam olarak şuraya koy:

```
android-kiosk/app/google-services.json
```
(Bu dosya gizli anahtarlar içerir, git'e girmez — zaten `.gitignore`'da.)

## 2. Android Studio'da aç
Android Studio kurulduktan sonra `android-kiosk/` klasörünü **Open an existing
project** ile aç. Gradle wrapper jar'ı bu repoda yok (binary dosya, elle
oluşturulamadı) — Android Studio ilk açılışta "gradle wrapper eksik, oluşturulsun
mu" diye soracak ya da otomatik ineceği bir Gradle sürümüyle projeyi senkronize
edecek; ikisi de sorun değil.

## 3. Yeni bir tableti provision etme (her tablet için tekrarlanır)

1. Tableti **fabrika ayarlarına döndür**, kurulum sihirbazında hiçbir Google
   hesabı ekleme (device owner ataması hesapsız cihaz şartı arıyor).
2. Wi-Fi'a bağla, **Ayarlar → Tablet hakkında → Yazılım sürümü/Yapı numarasına
   7 kez dokun** (geliştirici seçenekleri açılır), **Geliştirici seçenekleri →
   USB hata ayıklama**'yı aç. (Not: bazı Lenovo tabletlerinde "Model numarası"na
   basmak farklı bir gizli servis menüsü açıyor — oraya girme, geri çık.)
3. Tableti USB ile PC'ye bağla, Android Studio'dan **Run ▶** ile kur, ya da:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Device owner ata (kritik adım — kilitlemenin çalışması buna bağlı):
   ```
   adb shell dpm set-device-owner com.bizim.kiosk/.DeviceAdminReceiver
   ```
   Hata alırsan (`Not allowed to set the device owner...`) muhtemelen cihazda
   hâlâ bir hesap ya da başka bir device/profile owner vardır — fabrika
   ayarlarına dönüp hesap eklemeden tekrar dene.
5. Uygulamayı aç. Panelde birkaç saniye içinde **"İsimsiz cihaz"** olarak
   görünmeli. Panelde o cihaza tıkla → isim ver + **ya** kilitlenecek
   uygulamanın tam paket adını (`adb shell pm list packages` ile bulabilirsin)
   **ya da** tam ekran açılacak web adresini gir (ikisi birden olmaz) → Kaydet.
   Tablet birkaç saniye içinde kilitlenir.
6. PIN ile yerel kilit açma özelliğinin çalışması için gereken izni ver.
   `adb shell appops set ... SYSTEM_ALERT_WINDOW allow` bu tabletlerin
   ROM'unda (ZUI) güvenilir çalışmadı — bunun yerine:
   - Panelden o cihaza **"Kilidi aç"** gönder (Uzaktan Kontrol bölümü)
   - Tablette çıkan uygulama listesinden **Ayarlar/Settings**'e gir
   - **Uygulamalar → Kiosk → Diğer uygulamaların üzerinde göster**'i aç
   - Panelden **"Cihazı yeniden başlat"** gönder — izin ancak servis yeniden
     başlayınca (ilk denemesi izin yokken başarısız olmuş oluyor) devreye girer
7. USB'yi çıkar, geliştirici seçeneklerini/USB hata ayıklamayı kapat.

## Yerel kilit açma (PIN)
Ekranın **sağ yarısının herhangi bir yerini 5 saniye** basılı tutmak bir PIN
ekranı açar.
Doğru PIN (`app/build.gradle.kts` içindeki `EXIT_PIN`, şu an `2021`) girilirse
tablet normal Android'e döner (bildirim paneli, ana ekran vb. çalışır) —
süreye bağlı değil: admin panelden **"Kilitle"** demeden ya da kiosk'a/ana
ekrana dönülmeden kilitlenmez. PIN tüm tabletlerde aynı — değiştirmek için o
satırı düzenleyip APK'yı yeniden dağıtman yeterli.

## Uzaktan kontrol (panelden)
- **Uygulamayı yeniden başlat** — hedefi kapatıp yeniden açar
- **Cihazı yeniden başlat** — tam reboot
- **Kilidi aç** / **Kilitle** — yerel PIN ile aynı mantık, süresiz
- **Ekrana mesaj gönder** — 10 dakika boyunca ekranın üstünde küçük bir
  bildirim kutusu olarak durur, dokunuşları engellemez

## Ekran koruyucu (sadece web/link modundaki tabletlerde)
Panelin **Ekran Koruyucu** sekmesinden görsel URL'leri ve bekleme süresini
(saniye) ayarla. Süre dolunca görseller sırayla gösterilir; bir NFC kart
(USB "klavye gibi" okuyucu, kart id'sini yazıp Enter basar) okutulunca kilitli
sayfaya geri döner — düz dokunuşla açılmaz, bilerek kart isteniyor.

Native uygulamaya kilitli tabletlerde çalışmaz: o modda hedef uygulamanın
kendi ekranı bizim kontrolümüzde olmadığı için hareketsizliği güvenilir
şekilde ölçemiyoruz.

## Notlar
- `minSdk 26`, `targetSdk/compileSdk 35` — TB305FU'nun tam Android sürümünü
  öğrenince (`adb shell getprop ro.build.version.release`) gerekirse ayarlarız.
- Paket adını (`com.bizim.kiosk`) değiştirmek istersen `app/build.gradle.kts`
  (`namespace`, `applicationId`) ve device-owner komutundaki paket adını
  birlikte güncellemek gerekir.
