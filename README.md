# ai-safety-system

## PostgreSQL Backup ve Restore (Local/Demo)

Local PostgreSQL servisi `isg-postgres` Docker container'inda calisir.

### Backup alma

PostgreSQL custom-format backup olustur:

```bash
docker exec isg-postgres pg_dump -U isg_user -d isg_db -Fc -f /tmp/isg_db.dump
```

Backup dosyasini bilgisayara kopyala:

```bash
docker cp isg-postgres:/tmp/isg_db.dump ./isg_db.dump
```

### Restore

Restore islemi yeni ve bos bir veritabanina yapilmalidir. Asagidaki ornek mevcut `isg_db` veritabanini degistirmez.

Restore icin bos veritabani olustur:

```bash
docker exec isg-postgres psql -U isg_user -d postgres -c "CREATE DATABASE isg_restore_demo OWNER isg_user;"
```

Backup dosyasini PostgreSQL container'ina kopyala:

```bash
docker cp ./isg_db.dump isg-postgres:/tmp/isg_db.dump
```

Backup dosyasini restore et:

```bash
docker exec isg-postgres pg_restore -U isg_user -d isg_restore_demo /tmp/isg_db.dump
```

Restore edilen veritabanini kontrol et:

```bash
docker exec isg-postgres psql -U isg_user -d isg_restore_demo -c "\dt"
```

> Aktif gelistirme veritabaninin uzerine restore yapilmamalidir. Restore islemi bos bir hedef veritabanina yapilmalidir.


## Canonical MVP Runtime Contract

MVP local runtime icin canonical sozlesme root `README.md` ve `.env.example` dosyalaridir.

`.env.example`, paylasilan runtime degisken adlarini ve canonical local degerleri tanimlar. Gercek `.env` dosyasi local calisma ve secret degerleri icindir; repoya commit edilmemelidir.

### Canonical servis adresleri

| Servis | Canonical adres / port | Health / erisim |
| --- | --- | --- |
| Backend | `http://localhost:8080` | `GET /actuator/health` |
| Gateway | `http://localhost:8000` | `GET /health` |
| AI Worker | `http://localhost:8001` | `GET /health` |
| PostgreSQL | `ep-lively-scene-as9olf0d.c-4.eu-central-1.aws.neon.tech:5432/isg_db` | Neon PostgreSQL (TLS) |
| MinIO API | `http://localhost:9000` | API endpoint |
| MinIO Console | `http://localhost:9001` | Web console |

Canonical MinIO bucket adi `violation-media`'dir.

### Temel runtime env kurallari

- Backend, Gateway ve AI Worker ayni `INTERNAL_API_KEY` degerini kullanmalidir.
- `BACKEND_BASE_URL=http://localhost:8080`
- `GATEWAY_BASE_URL=http://localhost:8000`
- `AI_WORKER_BASE_URL=http://localhost:8001`
- `RECORDING_GATEWAY_BASE_URL=http://localhost:8000`
- `APP_CORS_ALLOWED_ORIGINS`, Backend REST CORS ve WebSocket allowed-origins icin ortak source-of-truth'tur.
- Local secret veya makineye ozel degerler `.env` icinde tutulmalidir.


## Local Calistirma

### Gereksinimler

- Java 21
- Python
- Docker Desktop
- Docker Compose

### 1. Local env hazirligi

Ilk kurulumda `.env.example` dosyasini `.env` olarak kopyalayin ve gerekli local/secret degerleri ayarlayin.

Mevcut `.env` dosyasinin uzerine yazmayin.

PowerShell oturumuna root `.env` degerlerini yuklemek icin:

```powershell
. .\scripts\import-env.ps1
```

Bu script env degerlerini yalnizca mevcut PowerShell process'ine yukler ve secret degerleri ekrana yazdirmaz.


### 2. MinIO'yu baslat

Root dizinde:

```powershell
docker compose up -d minio minio-init
docker compose ps
```

PostgreSQL `5432`, MinIO API `9000`, MinIO Console `9001` portunu kullanir.
`minio-init`, local MinIO icinde gerekli bucket hazirligini yapar.

### 3. Backend'i baslat

Root dizinde yeni bir terminal acin ve env'i yukledikten sonra:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

Backend varsayilan olarak `http://localhost:8080` adresinde calisir.
Flyway migrationlari Backend baslarken otomatik uygulanir. Shared Neon veritabanina yeni migrationlar otomatik uygulanabilecegi icin migration durumu kontrol edilmeden Backend Neon'a karsi baslatilmamalidir.

Health kontrolu:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```


### 4. AI Worker'i baslat

Backend ayaga kalktiktan sonra root dizinde:

```powershell
docker compose up -d ai-service
```

AI Worker canonical olarak `http://localhost:8001` adresinde calisir.

Health kontrolu:

```powershell
Invoke-RestMethod http://localhost:8001/health
```

### 5. Gateway'i baslat

Gateway giris noktasi `gateway/app/main.py` icindeki `app` nesnesidir.

Gateway Python runtime bagimliliklari `gateway/requirements.txt` dosyasinda tanimlidir.

Ilk kurulumda root dizinde:

```powershell
python -m pip install -r gateway\requirements.txt
```

Root dizinde yeni bir terminal acin, env'i yukleyin ve Gateway'i baslatin:

```powershell
. .\scripts\import-env.ps1
Set-Location gateway
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Gateway canonical olarak `http://localhost:8000` adresinde calisir.

Health kontrolu:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

Gateway health cevabi AI dispatch durumunu da raporlar.

### 6. Canonical startup sirasi

Local MVP icin onerilen sira:

1. Root `.env` degerlerini hazirla/yukle.
2. MinIO'yu baslat.
3. Backend'i baslat ve `/actuator/health` kontrolunu yap.
4. AI Worker'i baslat ve `/health` kontrolunu yap.
5. Gateway'i baslat ve `/health` kontrolunu yap.

### Demo verisini yukle

Asagidaki seed komutu yalniz local/demo Docker PostgreSQL icindir; canonical Neon veritabaninda calistirmayin:

```powershell
Get-Content backend\src\main\resources\db\seed\demo-seed.sql -Raw | docker exec -i isg-postgres psql -U isg_user -d isg_db
```

Demo kullanicilari icin ortak sifre: `123456`

Ornek admin kullanicisi: `admin@isgvision.local`

### Backend testlerini calistir

Root dizinde:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml test
```
