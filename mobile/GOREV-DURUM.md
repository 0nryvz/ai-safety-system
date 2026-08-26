# Mobil Görev Durumu (MOB-1 | Flutter & Gateway)

`1_Seda_Flutter_Gateway_Gorev_Plani` içindeki görev planının **yalnızca mobil
sorumluluk alanı**. Gateway, backend ve web kalemlerine dokunulmadı.

Son güncelleme: 21 Ağustos 2026 (STRIX marka + FPS/lifecycle denetimi)

---

## 1. Özet

| Adım | Konu | Durum |
|------|------|-------|
| ADIM 1 | Gateway/oturum sözleşmesini sabitle | Tamam |
| ADIM 2 | Kamera izni, ön izleme, kamera kontrolü | Tamam |
| ADIM 3 | Kamera seçimi ve güvenli session | Tamam (MVP: sabit session token) |
| ADIM 4 | Görüntü aktarım katmanı | Tamam |
| ADIM 5 | Bağlantı state machine ve reconnect | Tamam (reconnect aynı sessionId) |
| ADIM 6 | UX, tanılama, entegrasyon testleri | Tamam (otomatik); saha 15 dk cihaz gerektirir |

`flutter analyze`: temiz. `flutter test`: tüm birim testler geçiyor.

### Bu turda ek denetim
- `inactive` lifecycle yayın kesmiyor (yalnızca paused/hidden/detached)
- Gönderim FPS = başarılı HTTP; kamera FPS = kabul metronomu
- STRIX marka (`StrixBrand`), eski VIGIL asset/sayfa kaldırıldı
- README reconnect + AppConfig varsayılanları kodla hizalandı
- Varsayılan lens: arka kamera; “Değiştir” atamayı temizler
- Reconnect limiti dolunca Gateway oturumu kapatılır

---

## 2. Dünkü eksik listesi (P0–P2) kapanış

| # | Madde | Durum |
|---|-------|-------|
| P0-1 | Backend 2 kamera listesi + seçim | Tamam — `OperatorLoginPage` + `CameraSelectionPage` + offline demo auth |
| P0-1b | Kısa ömürlü token | MVP dışı (ekip kararı: `dev-session-token`) |
| P0-2 | Kamera izin akışı | Tamam — denied / kalıcı ret / Ayarları Aç |
| P0-3 | Gateway hata kodu eşlemesi | Tamam — `GatewayFailure` |
| P0-4 | Unit/widget testler | Tamam |
| P1-5 | Riverpod + features ayrımı | Tamam |
| P1-6 | README / sequence / demo | Tamam |
| P1-7 | Tanılama (kamera, reconnect, metrik) | Tamam — STRIX operatör paneli |
| P2-8 | Encode config | Tamam — `AppConfig` |
| P2-9 | Gönderim metrikleri | Tamam — `StreamMetrics` |
| P2-10 | 15 dk saha testi | Cihazda manuel (aşağıdaki koşu listesi) |
| P2-11 | Uçtan uca doğrulama | Otomatik testler + manuel koşu listesi |

### Bu turda düzeltilen kod hataları
- Stop→start 409 yarışı: cleanup bitmeden yeni open engellendi; 409’da stale close + tek retry
- Gateway HTTP timeout eklendi (`ApiClient`)
- `cancelUploads` in-flight sayaç bozulması giderildi
- Heartbeat eski oturumu bozmaması için generation/session kontrolü
- Arka plan pause: stop → close → dispose sırası
- Dispose’ta best-effort Gateway close
- JPEG encode null → failed (sessiz drop yok)
- Paralel upload limiti `AppConfig` ile hizalandı
- Giriş: genel catch + finally busy unlock; offline demo hesap
- İzin `unknown` artık granted sayılmıyor

---

## 3. Kapsam dışı (bilinçli)

**Kısa ömürlü camera session token.** MVP’de Gateway sabit `dev-session-token`
doğrular; mobil `AppConfig.cameraKey` (`--dart-define=CAMERA_KEY`) okur.

---

## 4. Cihazda manuel koşu (saha)

Telefon + `adb reverse tcp:8000` (+ isteğe bağlı 8080) ile:

1. Demo giriş: `admin@isgvision.local` / `123456` → kamera seç
2. İzin ver → önizleme → Gateway oturumu aç → gönderim FPS ≥ 5
3. Ön/arka lens (yayın kapalıyken)
4. Wi-Fi kapat → WEAK/RECONNECTING/OFFLINE
5. Arka plan → aktarım durur; dönüşte çift oturum yok
6. Stop → kare kesilir; hızlı start 409 vermez
7. İsteğe bağlı: 15 dk kesintisiz aktarım (crash yok)

---

## 5. Son teslim kontrol listesi

| # | Madde | Durum |
|---|-------|-------|
| 1 | Temiz kurulumdan sonra build oluyor | Tamam |
| 2 | Kamera izni, ön/arka kamera ve yerel ön izleme | Tamam |
| 3 | Aktif kamera seçimi + token kullanımı | Tamam (MVP sabit token) |
| 4 | Her yayında doğru sessionId; stop-start yeni session | Tamam |
| 5 | Görüntüler yalnızca Gateway’e gidiyor | Tamam |
| 6 | cameraId, sessionId, UTC timestamp | Tamam |
| 7 | Sınırlı queue / backpressure / drop | Tamam |
| 8 | Bağlantı durumları ve reconnect UI | Tamam |
| 9 | Arka plan, ağ kopması, manuel stop | Tamam (otomatik + manuel koşu) |
| 10 | Mobil ring buffer / 3 FPS / recorder / MinIO yok | Tamam |
