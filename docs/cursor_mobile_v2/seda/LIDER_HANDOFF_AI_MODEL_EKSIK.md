# Lider Handoff — Canlı ihlal uyarısı oluşmuyor (mobil dışı bloker)

**Tarih:** 22 Ağustos 2026  
**Kimden:** Seda (mobil / S0–S6)  
**Kime:** Proje lideri  
**Amaç:** Canlı çekimde uyarı gelmemesinin mobil eksiklik olmadığını belgelemek ve doğru ekibe iş atamak.

---

## 1. Sonuç (tek cümle)

Seda’nın mobil görevinde canlı ihlal uyarısını engelleyen bir eksik **yok**.  
Telefon kareleri Gateway’e ulaşıyor. Uyarıyı üretecek **AI model ağırlığı (`best.pt`) ortamda yok**; inference 503 dönüyor, backend’e detection gitmiyor, ihlal/STOMP oluşmuyor.

---

## 2. Seda / mobil tarafı (tamamlanan)

Seda kapsamı: Dashboard, Kameralar, İhlaller, Kullanıcılar, Bildirimler, UX; mevcut yayın akışını tüketmek. Yeni AI / Gateway / Backend / model işi **değil**.

Doğrulananlar:

- `flutter analyze` temiz; `flutter test` 332/332.
- Gerçek cihaz: TECNO CK6n, Android 14.
- Operatör konsolu **CANLI**; JPEG kareler Gateway’e gidiyor.
- Gateway: `POST /api/v1/sessions/{id}/frames` → **202 Accepted** (sürekli).
- Bildirim ekranı mevcut realtime altyapısını tüketiyor; canlı event gelmediği için empty state doğru.

Mobilin yapması gereken yayın + REST + uyarı UI’si çalışıyor. Kırılma mobil kodda değil.

---

## 3. Engelin kanıtı (değişiklik yapılmadan ölçüldü)

### 3.1 AI Worker

```text
GET http://127.0.0.1:8001/health
```

```json
{
  "status": "ok",
  "service": "ai-worker",
  "model": {
    "loaded": false,
    "version": "ppe-yolo26s-300ep-v1",
    "device": "cpu",
    "error": "[Errno 2] No such file or directory: '/models/best.pt'"
  }
}
```

Container `isg-ai-service` ayakta. `status: ok` yalnızca process’in ayakta olduğunu gösterir; **model yüklü değildir**.

Inference (aynı ortam, salt okuma):

```text
POST http://127.0.0.1:8001/internal/v1/inference/frames
→ 503  Model hazır değil
```

`ai-service` kodu: model yoksa 503, backend’e detection **göndermez**.

### 3.2 Dosya / volume

| Yer | Durum |
|---|---|
| Host `ai-service/models/` | `args.yaml`, `data.yaml` var; **`best.pt` yok** |
| Docker volume | `./ai-service/models` → `/models` (read-only) |
| Container `AI_MODEL_PATH` | `/models/best.pt` |

`ai-service/README.md`: model ağırlığı Git’e commit edilmez; local’de `./ai-service/models/best.pt` beklenir.

### 3.3 Gateway / backend

| Adım | Sonuç |
|---|---|
| Telefon → Gateway frames | **202** — çalışıyor |
| Gateway health `ai_dispatch_status` | `UP` — “AI’ye istek atacak şekilde yapılandırılmış”; model yüklü demek **değil** |
| Gateway `ai_dispatch_circuit_open` | `false` |
| Backend `GET /dashboard/recent-violations` (canlı çekim ~90 sn) | Yalnızca **seed** kayıtları; yeni ihlal yok |
| Backend detection ingest log | Canlı çekimde **yok** |

---

## 4. Sistem hattı (nerede koptuğu)

```text
[Mobil — Seda]          Kare gönderimi OK
        ↓
[Gateway]               202 Accepted OK
        ↓
[AI Worker]             503 — best.pt yok   ← KIRILMA
        ↓
[Backend detections]    çağrılmıyor
        ↓
[İhlal + STOMP uyarı]   oluşmuyor
        ↓
[Mobil Bildirimler]     gösterecek event yok (beklenen)
```

---

## 5. Atanması gereken iş (Seda kapsamında değil)

**Sahip:** AI Worker / DevOps / ortam kurulumundan sorumlu kişi (lider atar).

1. `ppe-yolo26s-300ep-v1` ağırlığını host’a koy:  
   `ai-service/models/best.pt`
2. `isg-ai-service` container’ının `/models/best.pt` gördüğünü doğrula.
3. `GET http://127.0.0.1:8001/health` → `model.loaded: true`
4. Inference 503 değil 202; ardından backend’de yeni detection/ihlal.

**İsteğe bağlı ikinci risk (model konduktan sonra):**  
`.env` içinde `GATEWAY_AI_DISPATCH_TIMEOUT_SECONDS=1.0`. CPU YOLO 1 sn’yi aşarsa Gateway timeout verebilir. Bu, **şu anki** bloker değil; model yokken timeout’a gelinmiyor.

---

## 6. Seda’nın canlı testte yaptıkları (doğru)

- Gerçek telefon + USB `adb reverse` (8080/8000/9000).
- Yayın: Kaynak-1 Kamera C (`CAM-WELDING-003`).
- Kaynak sahnesi (maskesiz kişi / `welding-test1`) kareye alındı.
- Gateway kareleri kabul etti.

Eksik olan çekim veya mobil entegrasyon değil; **AI artifact / local ortam**.

---

## 7. Liderden beklenen

- Bu işi AI/DevOps sahibine ata (`best.pt` + health `loaded: true`).
- Seda’ya “uyarı neden gelmedi” diye mobil bug kapatma görevi **verme**.
- Model ayağa kalktıktan sonra Seda aynı cihazla uyarıyı (Bildirimler + recent-violations) tekrar doğrulayabilir.

**Seda mobil kapsamı bu bloker için kapanmıştır.**
