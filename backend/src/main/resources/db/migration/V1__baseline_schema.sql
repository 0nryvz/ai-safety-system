-- =====================================================================================
-- V1 — Temel şema (baseline)
--
-- AI Destekli Gerçek Zamanlı İSG İhlal Tespit Sistemi / PRD v1.1 Bölüm 7, 10
-- Sahibi: Backend 1 (Altyapı, Veritabanı ve Raporlama)
--
-- ŞEMA STANDARTLARI — tüm modüller bu kurallara uyar:
--   1. Birincil anahtar: uuid, gen_random_uuid() varsayılanı (PostgreSQL 13+ yerleşik).
--   2. Tüm zaman alanları timestamptz'dir ve UTC saklanır. Kullanıcı saat dilimine
--      çevirme yalnızca arayüzde yapılır.
--   3. Her ana tabloda created_at / updated_at bulunur; updated_at bir trigger ile
--      garanti altına alınır (yalnızca uygulamaya güvenilmez).
--   4. Değişebilen tablolarda optimistic locking için version sütunu vardır.
--   5. Durum sütunları PostgreSQL enum tipi DEĞİL, varchar + CHECK kısıtıdır.
--      Gerekçe: PRD "yeni ihlal türleri ve model versiyonları mevcut sistemi bozmadan
--      eklenebilmelidir" diyor; enum tipine değer eklemek migration'ı kilitler ve
--      geri alınamaz hâle getirir.
--   6. Kullanıcı, kamera ve bölüm için silme yerine active bayrağı kullanılır;
--      geçmiş ihlal ilişkileri korunur.
--
-- UYARI: Bu dosya develop dalına girdikten sonra DEĞİŞTİRİLMEZ. Düzeltmeler yeni bir
-- migration ile yapılır; aksi hâlde Flyway checksum hatası ve ekip verisi kaybı oluşur.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- Ortak yardımcı: updated_at'i her UPDATE'te otomatik tazeler.
--
-- JPA auditing yalnızca Hibernate üzerinden yapılan yazmaları kapsar. Cleanup job'ları,
-- bakım scriptleri ve native update'ler de doğru updated_at üretmelidir; bu yüzden
-- garanti veritabanı seviyesindedir.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- -------------------------------------------------------------------------------------
-- departments — fabrika bölümleri (Backend 2 sahipliğinde kullanılır)
-- -------------------------------------------------------------------------------------
CREATE TABLE departments (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(40)  NOT NULL,
    name        varchar(120) NOT NULL,
    description varchar(500),
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT departments_code_uk UNIQUE (code),
    CONSTRAINT departments_name_uk UNIQUE (name)
);

CREATE TRIGGER departments_set_updated_at
    BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE departments IS 'Fabrika bolumleri. Vardiya sorumlusunun yetki kapsami bu tablo uzerinden belirlenir.';


-- -------------------------------------------------------------------------------------
-- roles — sistem rolleri
--
-- Rol adlarına CHECK kısıtı konulmamıştır: PRD "roller EN AZ Admin, İSG uzmanı ve
-- vardiya sorumlusu olmalıdır" diyor. Yeni bir rol eklemek migration gerektirmemelidir.
-- Zorunlu üç rol V3 migration'ında referans veri olarak yüklenir.
-- -------------------------------------------------------------------------------------
CREATE TABLE roles (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(40) NOT NULL,
    description varchar(255),
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT roles_name_uk UNIQUE (name)
);


-- -------------------------------------------------------------------------------------
-- users — sistem kullanıcıları
-- -------------------------------------------------------------------------------------
CREATE TABLE users (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(255) NOT NULL,
    -- BCrypt hash 60 karakterdir; alan ileride daha güçlü bir algoritmaya geçişe yer bırakır.
    -- Ham şifre hiçbir koşulda saklanmaz (PRD Bölüm 8, Güvenlik).
    password_hash varchar(100) NOT NULL,
    full_name     varchar(160) NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT users_email_format_chk CHECK (position('@' IN email) > 1)
);

-- E-posta büyük/küçük harf duyarsız benzersizdir: "Ali@x.com" ve "ali@x.com" aynı kullanıcıdır.
CREATE UNIQUE INDEX users_email_lower_uk ON users (lower(email));

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -------------------------------------------------------------------------------------
-- user_roles / user_departments — çoka-çok ilişkiler
-- -------------------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id     uuid        NOT NULL,
    role_id     uuid        NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Kullanılan bir rol silinemez: yetki kaybının sessizce olması engellenir.
    CONSTRAINT user_roles_role_fk FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE TABLE user_departments (
    user_id       uuid        NOT NULL,
    department_id uuid        NOT NULL,
    assigned_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_departments_pk PRIMARY KEY (user_id, department_id),
    CONSTRAINT user_departments_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_departments_department_fk FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT
);

COMMENT ON TABLE user_departments IS
    'Vardiya sorumlusunun gorebilecegi bolumler. Dashboard sorgularindaki yetki filtresi bu tabloyu kullanir.';


-- -------------------------------------------------------------------------------------
-- refresh_tokens — JWT yenileme belirteçleri (Backend 2 sahipliğinde kullanılır)
--
-- Ham token saklanmaz, yalnızca hash'i tutulur. Veritabanı sızsa bile mevcut oturumlar
-- ele geçirilemez.
-- -------------------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL,
    token_hash varchar(128) NOT NULL,
    issued_at  timestamptz  NOT NULL DEFAULT now(),
    expires_at timestamptz  NOT NULL,
    revoked_at timestamptz,
    user_agent varchar(255),
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_tokens_expiry_chk CHECK (expires_at > issued_at)
);


-- -------------------------------------------------------------------------------------
-- cameras — kamera tanımları (Backend 2 sahipliğinde kullanılır)
--
-- department_id bilinçli olarak NOT NULL'dır. Bölümü olmayan bir kamera, hiçbir vardiya
-- sorumlusunun yetki kapsamına girmeyen ihlaller üretirdi; bu da sessiz bir veri
-- görünmezliği yaratırdı. Kamera oluşturulurken bölüm ataması zorunludur.
-- -------------------------------------------------------------------------------------
CREATE TABLE cameras (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 varchar(120) NOT NULL,
    department_id        uuid         NOT NULL,
    location_description varchar(255),
    status               varchar(20)  NOT NULL DEFAULT 'OFFLINE',
    active               boolean      NOT NULL DEFAULT true,
    last_seen_at         timestamptz,
    -- MinIO nesne anahtarı; yasaklı alan poligonu bu referans görüntü üzerinde çizilir.
    reference_image_key  varchar(512),
    -- Mobil oturum doğrulaması için kamera anahtarının hash'i. Ham anahtar saklanmaz.
    camera_key_hash      varchar(128),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    version              bigint       NOT NULL DEFAULT 0,

    CONSTRAINT cameras_name_uk UNIQUE (name),
    CONSTRAINT cameras_department_fk FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT cameras_status_chk CHECK (status IN ('ONLINE', 'DEGRADED', 'OFFLINE'))
);

CREATE TRIGGER cameras_set_updated_at
    BEFORE UPDATE ON cameras
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -------------------------------------------------------------------------------------
-- camera_sessions — mobil kamera oturumları
-- -------------------------------------------------------------------------------------
CREATE TABLE camera_sessions (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id      uuid         NOT NULL,
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    started_at     timestamptz  NOT NULL DEFAULT now(),
    ended_at       timestamptz,
    last_frame_at  timestamptz,
    client_info    varchar(255),
    created_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT camera_sessions_camera_fk FOREIGN KEY (camera_id) REFERENCES cameras (id) ON DELETE CASCADE,
    CONSTRAINT camera_sessions_status_chk CHECK (status IN ('ACTIVE', 'CLOSED', 'TIMED_OUT')),
    CONSTRAINT camera_sessions_period_chk CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT camera_sessions_closed_chk CHECK (status = 'ACTIVE' OR ended_at IS NOT NULL)
);

-- PRD Bölüm 11 edge case: "Mobil uygulamanın aynı kamera için birden fazla oturum açması".
-- Bir kameranın aynı anda yalnızca tek bir açık oturumu olabilir; kural veritabanında zorlanır.
CREATE UNIQUE INDEX camera_sessions_single_active_uk
    ON camera_sessions (camera_id)
    WHERE status = 'ACTIVE';


-- -------------------------------------------------------------------------------------
-- restricted_zones — yasaklı alan poligonları
--
-- MVP kararı (BE-1 görev planı ADIM 3): ayrı nokta tablosu yerine doğrulanmış JSON alanı.
-- Poligon, referans görüntü üzerinde normalize edilmiş koordinatlarla saklanır:
--   [{"x": 0.12, "y": 0.44}, {"x": 0.60, "y": 0.44}, {"x": 0.60, "y": 0.90}]
-- Normalize saklama, kamera çözünürlüğü değiştiğinde poligonun geçersizleşmesini önler.
--
-- Şema seviyesindeki CHECK, "geçersiz polygon 400 döner" kuralının son savunma hattıdır:
-- API doğrulaması atlansa bile bozuk poligon veritabanına giremez.
-- -------------------------------------------------------------------------------------
CREATE TABLE restricted_zones (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id  uuid         NOT NULL,
    name       varchar(120) NOT NULL,
    polygon    jsonb        NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT restricted_zones_camera_fk FOREIGN KEY (camera_id) REFERENCES cameras (id) ON DELETE CASCADE,
    CONSTRAINT restricted_zones_name_uk UNIQUE (camera_id, name),

    -- Poligon bir dizi olmalı ve en az üç köşe içermelidir.
    CONSTRAINT restricted_zones_polygon_array_chk CHECK (
        jsonb_typeof(polygon) = 'array' AND jsonb_array_length(polygon) >= 3
    ),
    -- Her köşede x ve y bulunmalıdır.
    CONSTRAINT restricted_zones_polygon_keys_chk CHECK (
        NOT (polygon @? '$[*] ? (!exists(@.x) || !exists(@.y))')
    ),
    -- x ve y sayısal olmalıdır.
    CONSTRAINT restricted_zones_polygon_types_chk CHECK (
        NOT (polygon @? '$[*] ? (@.x.type() <> "number" || @.y.type() <> "number")')
    ),
    -- Koordinatlar normalize edilmiş, yani [0, 1] aralığında olmalıdır.
    CONSTRAINT restricted_zones_polygon_range_chk CHECK (
        NOT (polygon @? '$[*] ? (@.x < 0 || @.x > 1 || @.y < 0 || @.y > 1)')
    )
);

CREATE TRIGGER restricted_zones_set_updated_at
    BEFORE UPDATE ON restricted_zones
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -------------------------------------------------------------------------------------
-- violations — ihlal kayıtları (Backend 3 sahipliğinde kullanılır)
--
-- KRİTİK TASARIM KARARI: ihlalin yaşam döngüsü ile kullanıcının inceleme durumu iki AYRI
-- sütundur ve asla tek bir enum içinde karıştırılmaz.
--   lifecycle_status : ihlalin sistemdeki teknik durumu   (ACTIVE → PREPARING → COMPLETED | ERROR)
--   review_status    : İSG uzmanının verdiği karar        (UNREVIEWED → REVIEWED | CONFIRMED | FALSE_ALARM)
-- Karıştırılsaydı "kaydı hazırlanan ama henüz incelenmemiş ihlal" gibi tamamen geçerli bir
-- durum ifade edilemezdi.
--
-- department_id bilinçli olarak denormalize edilmiştir. Yetki filtresi ve dashboard
-- sorguları bu sütunu doğrudan kullanır; kamera bir başka bölüme taşınsa bile geçmiş
-- ihlaller oluştukları bölümde kalır — rapor geçmişi geriye dönük değişmez.
-- -------------------------------------------------------------------------------------
CREATE TABLE violations (
    id                 uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id          uuid          NOT NULL,
    department_id      uuid          NOT NULL,
    camera_session_id  uuid,
    restricted_zone_id uuid,
    violation_type     varchar(60)   NOT NULL,
    started_at         timestamptz   NOT NULL,
    ended_at           timestamptz,
    -- 0.0000 - 1.0000 arası; ihlali doğrulayan karelerin temsilci confidence değeri.
    confidence         numeric(5, 4) NOT NULL,
    model_version      varchar(60)   NOT NULL,
    lifecycle_status   varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    review_status      varchar(20)   NOT NULL DEFAULT 'UNREVIEWED',
    cover_image_key    varchar(512),
    -- KPI ölçümü için: ilk detection anı ile alert gönderim anı arasındaki fark
    -- "alert gecikmesi"dir (PRD Bölüm 13 hedefi: 2 saniyeden az).
    detected_at        timestamptz,
    alert_sent_at      timestamptz,
    reviewed_by        uuid,
    reviewed_at        timestamptz,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    version            bigint        NOT NULL DEFAULT 0,

    CONSTRAINT violations_camera_fk FOREIGN KEY (camera_id) REFERENCES cameras (id) ON DELETE RESTRICT,
    CONSTRAINT violations_department_fk FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT violations_session_fk FOREIGN KEY (camera_session_id) REFERENCES camera_sessions (id) ON DELETE SET NULL,
    CONSTRAINT violations_zone_fk FOREIGN KEY (restricted_zone_id) REFERENCES restricted_zones (id) ON DELETE SET NULL,
    CONSTRAINT violations_reviewer_fk FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT violations_lifecycle_chk CHECK (lifecycle_status IN ('ACTIVE', 'PREPARING', 'COMPLETED', 'ERROR')),
    CONSTRAINT violations_review_chk CHECK (review_status IN ('UNREVIEWED', 'REVIEWED', 'CONFIRMED', 'FALSE_ALARM')),
    CONSTRAINT violations_confidence_chk CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT violations_period_chk CHECK (ended_at IS NULL OR ended_at >= started_at),
    -- Tamamlanmış ya da hatalı bir ihlalin bitiş zamanı olmak zorundadır; aksi hâlde
    -- süre bazlı KPI'lar sessizce yanlış hesaplanır.
    CONSTRAINT violations_terminal_end_chk CHECK (
        lifecycle_status NOT IN ('COMPLETED', 'ERROR') OR ended_at IS NOT NULL
    ),
    -- İncelenmiş bir ihlalin kim tarafından ve ne zaman incelendiği kayıtsız kalamaz.
    CONSTRAINT violations_review_audit_chk CHECK (
        review_status = 'UNREVIEWED' OR reviewed_at IS NOT NULL
    )
);

CREATE TRIGGER violations_set_updated_at
    BEFORE UPDATE ON violations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN violations.department_id IS
    'Ihlalin olustugu andaki bolum. Yetki filtresi ve dashboard icin bilincli olarak denormalize edilmistir.';


-- -------------------------------------------------------------------------------------
-- violation_status_history — durum geçiş günlüğü
--
-- Hem lifecycle hem review geçişleri tek tabloda, status_kind ile ayrılarak tutulur.
-- İki ayrı tablo, aynı sorguları iki kez yazmayı gerektirirdi.
-- -------------------------------------------------------------------------------------
CREATE TABLE violation_status_history (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_id uuid         NOT NULL,
    status_kind  varchar(12)  NOT NULL,
    from_status  varchar(20),
    to_status    varchar(20)  NOT NULL,
    -- NULL = geçişi sistem yaptı (örneğin cooldown bitişi), dolu = kullanıcı yaptı.
    changed_by   uuid,
    changed_at   timestamptz  NOT NULL DEFAULT now(),
    note         varchar(500),

    CONSTRAINT violation_status_history_violation_fk
        FOREIGN KEY (violation_id) REFERENCES violations (id) ON DELETE CASCADE,
    CONSTRAINT violation_status_history_user_fk
        FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT violation_status_history_kind_chk CHECK (status_kind IN ('LIFECYCLE', 'REVIEW')),
    CONSTRAINT violation_status_history_transition_chk CHECK (from_status IS NULL OR from_status <> to_status)
);


-- -------------------------------------------------------------------------------------
-- recordings — olay bazlı video klipleri (Backend 4 sahipliğinde kullanılır)
--
-- violation_id üzerindeki UNIQUE kısıt, PRD'nin "her doğrulanmış ihlal için TEK bir olay
-- klibi oluşturulmalıdır" kuralını doğrudan veritabanında garanti eder. Gateway aynı ihlal
-- için ikinci bir kayıt komutu üretse bile ikinci satır reddedilir.
-- -------------------------------------------------------------------------------------
CREATE TABLE recordings (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_id         uuid        NOT NULL,
    status               varchar(20) NOT NULL DEFAULT 'PENDING',
    -- MinIO nesne anahtarı. Web'e asla doğrudan URL verilmez; erişim backend'in ürettiği
    -- süreli bağlantı üzerinden sağlanır (PRD Bölüm 8, Güvenlik).
    object_key           varchar(512),
    duration_ms          integer,
    size_bytes           bigint,
    retry_count          integer     NOT NULL DEFAULT 0,
    checksum             varchar(128),
    error_code           varchar(60),
    recording_started_at timestamptz,
    ready_at             timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    version              bigint      NOT NULL DEFAULT 0,

    CONSTRAINT recordings_violation_uk UNIQUE (violation_id),
    CONSTRAINT recordings_violation_fk FOREIGN KEY (violation_id) REFERENCES violations (id) ON DELETE CASCADE,
    CONSTRAINT recordings_status_chk CHECK (status IN ('PENDING', 'RECORDING', 'PROCESSING', 'READY', 'ERROR')),
    CONSTRAINT recordings_duration_chk CHECK (duration_ms IS NULL OR duration_ms > 0),
    CONSTRAINT recordings_size_chk CHECK (size_bytes IS NULL OR size_bytes > 0),
    CONSTRAINT recordings_retry_chk CHECK (retry_count >= 0),
    -- READY bir klip, oynatılabilmesi için gereken tüm bilgilere sahip olmak zorundadır.
    -- Bu kısıt olmadan web paneli "hazır" görünen ama nesne anahtarı olmayan bir klip
    -- gösterebilirdi (PRD Bölüm 11 edge case).
    CONSTRAINT recordings_ready_complete_chk CHECK (
        status <> 'READY'
        OR (object_key IS NOT NULL AND duration_ms IS NOT NULL
            AND size_bytes IS NOT NULL AND ready_at IS NOT NULL)
    ),
    -- Hatalı bir kaydın nedeni her zaman kayıtlıdır; "sebepsiz hata" durumu oluşamaz.
    CONSTRAINT recordings_error_code_chk CHECK (status <> 'ERROR' OR error_code IS NOT NULL)
);

CREATE TRIGGER recordings_set_updated_at
    BEFORE UPDATE ON recordings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -------------------------------------------------------------------------------------
-- system_settings — çalışma zamanı yapılandırması
--
-- PRD Bölüm 5: "Sistem yöneticisi ihlal doğrulama, cooldown, tampon ve kayıt sonrası süre
-- ayarlarını değiştirebilmelidir." Bu değerler yeniden derleme gerektirmeden değişebilmelidir.
-- -------------------------------------------------------------------------------------
CREATE TABLE system_settings (
    setting_key varchar(80)  PRIMARY KEY,
    value       varchar(255) NOT NULL,
    value_type  varchar(20)  NOT NULL,
    description varchar(500),
    updated_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT system_settings_user_fk FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT system_settings_type_chk CHECK (value_type IN ('INTEGER', 'DECIMAL', 'BOOLEAN', 'STRING'))
);

CREATE TRIGGER system_settings_set_updated_at
    BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
