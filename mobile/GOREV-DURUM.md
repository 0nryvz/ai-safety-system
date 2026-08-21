# Mobil Görev Durumu (MOB-1 | Flutter & Gateway)

`proje-gorevim.md` içindeki görev planının **yalnızca mobil sorumluluk alanı**.
Gateway, backend ve web kalemleri kapsam dışı; Gateway koduna dokunulmadı.

Son güncelleme: 21 Ağustos 2026

---

## 1. Özet

| Adım | Konu | Durum |
|------|------|-------|
| ADIM 1 | Gateway/oturum sözleşmesini sabitle | Tamam |
| ADIM 2 | Kamera izni, ön izleme, kamera kontrolü | Tamam |
| ADIM 3 | Kamera seçimi ve güvenli session | Tamam |
| ADIM 4 | Görüntü aktarım katmanı | Tamam |
| ADIM 5 | Bağlantı state machine ve reconnect | Tamam |
| ADIM 6 | UX, tanılama, entegrasyon testleri | Kısmen — uzun süreli saha testi kaldı |

`flutter analyze`: temiz. `flutter test`: 61 test geçiyor.

---

## 2. Bu turda tamamlananlar

### Kimlik ve sözleşme (ADIM 1, 3)
- Sabit `camera-1` kaldırıldı. Kamera kimliği artık sırayla: kullanıcının
  Backend 2 listesinden seçtiği UUID → `--dart-define=CAMERA_ID` → cihazda
  saklanan geliştirme UUID'si.
- `sessionId` timestamp yerine UUID v4; her yayında yeni oturum.
- Heartbeat ve close gövdeleri sözleşmedeki camelCase `cameraId` alanına
  düzeltildi (önceden `camera_id` gönderiliyordu).
- Sabit `10.0.2.2:8000` adresi kaldırıldı; tüm endpoint'ler yapılandırmadan.

### Backend 2 entegrasyonu (ADIM 3)
- `BackendClient`: `POST /api/v1/auth/login` ve `GET /api/v1/cameras`.
- `CameraPickerSheet`: giriş yapıp yetkili kamera listesinden seçim. Pasif
  kameralar seçilemiyor. Seçim cihazda kalıcı.
- JWT yalnızca bellekte tutuluyor; ekranda ve logda gösterilmiyor.
- Oturum token'ı ekip kararıyla MVP boyunca sabit (`dev-session-token`);
  backend'e bağlanmıyor. Değer koda gömülü değil, `--dart-define=CAMERA_KEY`
  ile veriliyor.

### Kamera izni (ADIM 2)
- Native Android izin kanalı: `checkCameraPermission`, `requestCameraPermission`,
  `openAppSettings`.
- Reddedildi ile kalıcı reddedildi ayrımı `shouldShowRequestPermissionRationale`
  ile yapılıyor; kalıcı rette ekranda **Ayarları Aç** butonu çıkıyor.
- Kamera başlatma hataları (başka uygulama kullanıyor, erişim kısıtlı, kamera
  koptu) ayrı mesajlara eşlendi.

### Hata kodu eşlemesi (ADIM 1, 3)
- `GatewayFailure` modeli: 401, 403, 404, 409, 413, 415, 422, 503, 5xx ve ağ
  hatası ayrı türlere eşleniyor.
- Her türün kullanıcı mesajı ve yeniden denenebilirlik bilgisi var. Token,
  pasif kamera ve oturum çakışması hatalarında boşuna reconnect denenmiyor.
- Servisler `bool` yerine tipli sonuç döndürüyor.

### Aktarım katmanı (ADIM 4)
- Çözünürlük, JPEG kalitesi, hedef FPS ve eşzamanlılık sınırı yapılandırmadan.
- `StreamMetrics`: gönderilen, başarısız ve düşen kare sayıları ile son
  başarılı gönderim zamanı.
- Kare başına tanılama logları `FRAME_DIAGNOSTICS` bayrağının arkasına alındı;
  üretim kodunda saniyede 15 `print` kalmadı.

### Yapı ve state yönetimi (ADIM 5, önerilen düzen)
- 1372 satırlık `main.dart` parçalandı. `main.dart` artık 28 satır.
- Plandaki düzen kuruldu: `features/camera`, `features/session`,
  `features/streaming`, `core/config`, `core/network`, `core/error`,
  `shared/widgets`.
- Tüm durum makinesi `StreamingController` (Riverpod `Notifier`) içinde tek
  kaynakta toplandı; widget'lar kendi bağlantı boolean'larını tutmuyor.

### Tanılama ve UX (ADIM 6)
- Ürün adı **VIGIL**; launcher ikonu ve Space Grotesk temalı operatör dashboard.
- Uygulama webcam değil: açılışta fabrika kamerası ataması zorunlu.
- Dashboard KPI şeridi (gönderim/kamera FPS, durum), atanmış kamera kartı,
  yerel önizleme ve Gateway CTA.
- Backend kapalıysa demo kamera listesi (seed UUID'leri).

### Testler (ADIM 6)
61 test: Gateway hata kodu eşlemesi, oturum servisi sözleşmesi (camelCase
gövde, durum kodları, ağ hatası), frame metadata header'ları ve UTC damgası,
Backend 2 istemcisi, aktarım sayaçları, bağlantı durum modeli, overlay
widget'ları.

### Dokümantasyon (ADIM 1, 6)
`mobile/README.md`: kurulum, yapılandırma tablosu, örnek payload'lar, sequence
diyagramı, bağlantı durumları, reconnect ve sessionId kuralı, backpressure
politikası, hata kodu tablosu, demo akışı, proje yapısı.

---

## 3. Kapsam dışı bırakılan

**Kısa ömürlü camera session token'ı.** Ekip kararıyla MVP sürecinde token
Backend 2'ye bağlanmıyor; Gateway sabit `dev-session-token` değerini doğruluyor.
Mobil tarafta token tek noktadan (`AppConfig.cameraKey`) okunduğu için
MVP sonrasında geçiş yapılmak istenirse yalnızca o okuma değişecek.

`camera_key_hash` sütunu veritabanı şemasında ileriye dönük duruyor, MVP'de
kullanılmıyor.

---

## 4. Kalan işler

### Cihazda yapılması gerekenler

**Uzun süreli saha testleri.** Henüz koşulmadı:
- 15 dakikalık kesintisiz aktarımda crash olmaması
- Düşük Wi-Fi'da davranış
- Ekran kilidi ve uzun arka plan
- Kamera önünün kapanması

**Refactor sonrası uçtan uca doğrulama.** Kod tümüyle yeniden yapılandırıldığı
için cihazda tekrar koşulmalı: izin akışı, kamera seçimi, 15 FPS'in korunması,
stop sonrası kare gönderiminin kesilmesi, Gateway loglarıyla `sessionId` ve
timestamp eşleşmesi.

---

## 5. Son teslim kontrol listesi

| # | Madde | Durum |
|---|-------|-------|
| 1 | Temiz kurulumdan sonra build oluyor | Tamam |
| 2 | Kamera izni, ön/arka kamera ve yerel ön izleme çalışıyor | Tamam |
| 3 | Aktif backend kamerası seçiliyor, token kullanılabiliyor | Tamam — kısa ömürlü token MVP kapsamı dışı |
| 4 | Her yayında doğru sessionId; stop-start yeni session | Tamam |
| 5 | Görüntüler yalnızca Gateway'e gidiyor | Tamam |
| 6 | cameraId, sessionId ve UTC timestamp akışla gönderiliyor | Tamam |
| 7 | Sınırlı queue/backpressure ve eski frame drop | Tamam |
| 8 | Bağlantı durumları ve reconnect kullanıcıya gösteriliyor | Tamam |
| 9 | Arka plan, ağ kopması ve manuel stop senaryoları test edildi | Kısmen — otomatik testler var, saha testi kaldı |
| 10 | Mobil ring buffer / 3 FPS sampling / recorder / MinIO yapmıyor | Tamam |
