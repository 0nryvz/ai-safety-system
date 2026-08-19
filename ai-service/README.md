# ai-service (AI Worker)

Gateway'den gelen JPEG kareyi alıp model inference çalıştıran ve sonucu
Spring Boot backend'e (`POST /internal/v1/detections`) ileten FastAPI servisi.

## Durum
- [x] Adım 1 - FastAPI iskeleti, config, `/health`
- [x] Adım 3 - `POST /internal/v1/inference/frames` (header/body kontratı, eventId passthrough)
- [x] Adım 4 - Backend'e detection gönderimi (`BackendDetectionClient`, bounded retry, 4xx/5xx ayrımı)
- [x] Adım 0 - Model artifact teslimi tamamlandı: `models/best.pt` (yolo26s, imgsz=640, 6 class)
- [x] Adım 2 - `ModelRunner.load()` / `predict()` gerçek Ultralytics YOLO ile dolduruldu
- [x] `config/class_mapping.json` - 6 class dolduruldu, vizör yok
- [x] Adım 6 (kısmi) - `Dockerfile` hazır; `docker-compose.ai-service.yml` root compose'a taşınacak referans parça
- [ ] Adım 5 - Gerçek modelle 3 FPS throughput ölçümü, Gateway ile E2E test (Gateway olmadan bu ortamda koşulamaz)
- [ ] Adım 6 (tam) - root `docker-compose.yml`'a BE/DevOps ile birleştirme
- [ ] Confidence/IoU eşiklerinin gerçek görüntülerle ince ayarı (şu an varsayılan 0.5 / 0.45)

## Model bilgisi (Adım 0 handoff)
- Mimari: YOLO26s (Ultralytics), `imgsz=640`, 300 epoch
- Class'lar (`models/data.yaml`): `Person, gloves, welding, welding_apron, welding_jacket, welding_mask`
- Hepsi backend label'larıyla birebir eşleşiyor (`Person` -> `person`); vizör yok
- Ağırlık: `models/best.pt` (repo'ya commit edilmeyecek, gerçek dağıtımda volume mount önerilir)

## Yerel çalıştırma

```bash
cd ai-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env   # AI_MODEL_PATH varsayılan olarak ./models/best.pt'ye işaret ediyor

uvicorn app.main:app --reload --port 8001
```

Health check:

```bash
curl http://localhost:8001/health
```

Model başarıyla yüklendiyse beklenen:

```json
{
  "status": "ok",
  "service": "ai-worker",
  "model": {
    "loaded": true,
    "version": "ppe-yolo26s-300ep-v1",
    "device": "cpu",
    "error": null
  }
}
```

## Test

```bash
pytest
```

## Notlar
- Model path / backend URL kesinlikle hard-code edilmez, `.env` üzerinden okunur (bkz. `app/core/config.py`).
- Model, uygulama açılışında (`lifespan`) bir kez yüklenir; request başına reload edilmez.
- Backend'in kabul ettiği label listesi: `person, welding, welding_mask, welding_apron, gloves, welding_jacket`. Vizör (`welding_visor` vb.) desteklenmez ve mapping'e eklenmeyecek.

## Sorun Giderme

### `/health` içinde `model.loaded: false`
`model.error` alanındaki mesaja bakın; en sık görülen durumlar:

**"AI_MODEL_PATH tanımlı değil"** → `.env` dosyanız yok veya `AI_MODEL_PATH` boş. `.env.example`'ı `.env` olarak kopyalayın.

**Windows'ta `OSError: [WinError 1114] ... c10.dll ...` (torch import ederken çöküyor)**
Bu, model veya kodla ilgili değil — `torch`'un Windows DLL'lerini yükleyememesi. Sırasıyla:
1. [Microsoft Visual C++ Redistributable x64](https://aka.ms/vs/17/release/vc_redist.x64.exe) kurun ve bilgisayarı yeniden başlatın (en sık çözüm).
2. `pip uninstall torch torchvision torchaudio -y && pip cache purge && pip install torch==2.9.1 --no-cache-dir` ile temiz yeniden kurun.
3. CPU'nuzun AVX2 desteklediğini doğrulayın; desteklemiyorsa standart PyPI torch wheel'i çalışmaz.
4. Hâlâ çözülmezse `.venv` klasörünü tamamen silip yeniden oluşturun.

**"model mimarisi/sınıfı tanınmıyor" tarzı unpickle hatası** → `ultralytics` sürümü modelin eğitildiği sürümle (8.4.111) uyuşmuyor. `pip install ultralytics==8.4.111` ile sabitleyin (requirements.txt zaten bu sürüme pinli).
