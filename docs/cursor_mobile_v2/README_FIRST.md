# Cursor Pro — PRD v2.0 Mobil Geliştirme Paketi

Bu paket, `mobile/` altındaki PRD v2.0 geliştirmelerini Cursor IDE içinde **gereksiz context/token harcamadan** yapmak için hazırlanmıştır.

## Ana kullanım kuralı

Cursor'a bütün PRD'yi, bütün PDF'leri ve bütün repoyu her görevde tekrar verme.

Her yeni görevde yalnızca:
1. `00_SHARED_CONTEXT.md`
2. `01_CONTRACT_SNAPSHOT.md`
3. O an çalışacağın **tek görev dosyası**
4. Görev dosyasının `READ FIRST` bölümündeki kod dosyaları

verilsin.

Kod yazmaya başlamadan Cursor'a şunu uygulat:

> Önce verilen dosyaları oku. Repo genelinde geniş tarama yapma. Sadece bu görev için gereken ek dosya gerçekten lazımsa ara. Önce kısa bir uygulama planı çıkar, sonra değişiklik yap. Backend/Gateway/AI/Web koduna yazma.

---

## Model yönlendirmesi

Bu pakette görevler iki sınıfa ayrılmıştır.

### `MODEL: NORMAL`
Hızlı/ucuz model ile başla.

Uygun işler:
- DTO/model oluşturma
- REST servis metotları
- Form/list/card ekranları
- Widget testleri
- Loading/empty/error state
- Basit navigation bağlama
- Mechanical refactor
- README/runbook
- Test çalıştırma ve küçük compile fixleri

Normal model bir görevde sürekli aynı hataya dönüyorsa veya çözüm 3+ feature/state katmanını etkiliyorsa güçlü modele geç.

### `MODEL: STRONG`
Güçlü reasoning modeli kullan.

Uygun işler:
- AppShell + auth/session mimarisi
- Refresh/logout yarışları
- 401/403 merkezi davranış
- STOMP framing / reconnect / duplicate-safe store
- REST recovery reconciliation
- Optimistic version/conflict davranışı
- Birden fazla feature'ın ortak contract entegrasyonu
- E2E'de hangi servisin hatalı olduğunu teşhis etme
- Concurrency/lifecycle/session race

### Token tasarrufu için escalation kuralı

1. Görev `NORMAL` ise normal modelle başla.
2. Model kodu ürettikten sonra test başarısızsa aynı modele **bir kez** hata çıktısını ver.
3. İkinci denemede hâlâ temel sebep bulunamıyorsa güçlü modele geç.
4. Güçlü modelden sadece **teşhis + düzeltme planı** al.
5. Plan netleşince mechanical uygulamayı tekrar normal modele verebilirsin.

---

## Önerilen sıra

### Onur
1. `onur/O0_CONTRACT_SEED_STRONG.md`
2. `onur/O1_AUTH_ROLE_NAV_STRONG.md`
3. `onur/O2_REALTIME_STOMP_STRONG.md`
4. `onur/O3_CLIP_PLAYER_NORMAL.md`
5. `onur/O4_INTEGRATION_REVIEW_STRONG.md`
6. `onur/O5_CAMERA_REGRESSION_NORMAL.md`
7. `onur/O6_FINAL_E2E_STRONG.md`

### Seda
Onur'un O0 seed commitinden sonra:
1. `seda/S0_DASHBOARD_NORMAL.md`
2. `seda/S1_CAMERAS_NORMAL.md`
3. `seda/S2_VIOLATIONS_STRONG.md`
4. `seda/S3_USERS_NORMAL.md`
5. `seda/S4_NOTIFICATIONS_NORMAL.md`
6. `seda/S5_UX_NORMAL.md`
7. `seda/S6_TEST_HANDOFF_NORMAL.md`

---

## Cursor'a her görevde verilecek kısa başlangıç mesajı

```text
@00_SHARED_CONTEXT.md
@01_CONTRACT_SNAPSHOT.md
@<GOREV_DOSYASI>.md

Bu görev dışında feature açma.
Önce READ FIRST dosyalarını incele.
Repo genelinde gereksiz tarama yapma.
Backend/Gateway/AI/Web koduna yazma.
Önce 5-10 maddelik plan çıkar.
Sonra yalnız WRITE SCOPE içindeki dosyalarda değişiklik yap.
Her mantıksal değişiklikten sonra ilgili testleri çalıştır.
Bir contract uyuşmazlığı bulursan endpoint/enum uydurma; dur ve bana bildir.
```

---

## Büyük context ne zaman verilmeli?

PRD veya görev planı PDF'sini Cursor'a yalnız şu durumlarda tekrar ver:
- Ürün kapsamı konusunda gerçekten belirsizlik varsa.
- Mobilde yapılmalı/yapılmamalı kararı tartışılıyorsa.
- Backend contractı ile ürün gereksinimi çelişiyorsa.

Kod yazdırmak için her seferinde PDF vermek gereksiz token tüketir. Bu pakette gereken kapsam özetlenmiştir.
