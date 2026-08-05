-- =====================================================================================
-- V2 — İndeksler ve veritabanı seviyesinde idempotency garantisi
--
-- Sahibi: Backend 1
--
-- Bu migration iki işi yapar:
--   1. Dashboard ve ihlal geçmişi filtrelerinin indeks üzerinden çalışmasını sağlar
--      (PRD Bölüm 8: dashboard normal koşullarda 3 saniyeden kısa sürede açılmalıdır).
--   2. PRD'nin bazı davranış kurallarını veritabanı kısıtına dönüştürür; böylece kural
--      yalnızca uygulama koduna emanet edilmez.
--
-- NOT: PostgreSQL yabancı anahtarlar için otomatik indeks oluşturmaz. Join ve
-- ON DELETE davranışlarında tablo taraması yaşanmaması için FK sütunları açıkça
-- indekslenmiştir.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- violations — dashboard ve ihlal geçmişi ekranlarının ana tablosu
-- -------------------------------------------------------------------------------------

-- Tüm bölümlere yetkili İSG uzmanının tarih aralığı sorguları ve "son ihlaller" listesi.
CREATE INDEX violations_started_at_idx
    ON violations (started_at DESC);

-- Vardiya sorumlusunun bölüm kapsamlı sorguları. Sütun sırası kritiktir: önce eşitlik
-- filtresi (department_id), sonra aralık filtresi (started_at).
CREATE INDEX violations_department_started_at_idx
    ON violations (department_id, started_at DESC);

-- Kamera bazlı dağılım ve kamera detay ekranı.
CREATE INDEX violations_camera_started_at_idx
    ON violations (camera_id, started_at DESC);

-- Tür bazlı dağılım ve "en sık görülen ihlal türü" sorgusu.
CREATE INDEX violations_type_started_at_idx
    ON violations (violation_type, started_at DESC);

-- İnceleme durumuna göre filtreleme (İSG uzmanının iş kuyruğu).
CREATE INDEX violations_review_status_idx
    ON violations (review_status, started_at DESC);

-- "Aktif ihlal sayısı" sürekli sorgulanan ama sonuç kümesi çok küçük olan bir metriktir.
-- Kısmi indeks, tüm tabloyu değil yalnızca açık ihlalleri kapsar; bu yüzden tablo
-- büyüdükçe indeks büyümez.
CREATE INDEX violations_active_idx
    ON violations (started_at DESC)
    WHERE lifecycle_status = 'ACTIVE';

-- Klip hazırlığı bekleyen veya hata almış ihlaller (recording KPI'ları ve yeniden deneme).
CREATE INDEX violations_pending_lifecycle_idx
    ON violations (lifecycle_status, started_at DESC)
    WHERE lifecycle_status IN ('PREPARING', 'ERROR');

-- ---------------------------------------------------------------------------------
-- DAVRANIŞ KURALININ VERİTABANINDA ZORLANMASI
--
-- PRD Bölüm 7: "Aynı ihlal devam ederken her kare için yeni kayıt oluşturulmamalıdır."
--
-- Bu kuralın asıl sahibi Backend 3'ün zaman bazlı doğrulama ve cooldown mantığıdır.
-- Ancak yarış durumu (aynı anda gelen iki detection sonucu) veya yeniden başlatma
-- sonrası uzlaştırma sırasında mantık atlanabilir. Kısmi unique indeks, bir kamera ve
-- ihlal türü için aynı anda yalnızca TEK bir ACTIVE ihlal bulunmasını garanti eder;
-- ikinci kayıt denemesi 409 Conflict'e dönüşür.
--
-- Kısmi (WHERE'li) indeks kullanılmasının nedeni: kapanmış ihlaller kısıta dahil
-- olmamalıdır, aynı kamerada aynı türden yüzlerce geçmiş ihlal elbette olabilir.
-- ---------------------------------------------------------------------------------
CREATE UNIQUE INDEX violations_single_active_per_camera_type_uk
    ON violations (camera_id, violation_type)
    WHERE lifecycle_status = 'ACTIVE';

-- FK sütunları (join ve ON DELETE SET NULL performansı)
CREATE INDEX violations_session_idx ON violations (camera_session_id);
CREATE INDEX violations_zone_idx ON violations (restricted_zone_id);
CREATE INDEX violations_reviewer_idx ON violations (reviewed_by);


-- -------------------------------------------------------------------------------------
-- recordings — klip durumu ve başarı oranı KPI'sı
-- -------------------------------------------------------------------------------------

-- "Kayıt hazırlama durumu" sütunu dashboard'da her satır için gösterilir.
CREATE INDEX recordings_status_idx ON recordings (status);

-- Yeniden deneme kuyruğu: yalnızca hatalı kayıtlar. Tablo büyüdükçe bu indeks büyümez.
CREATE INDEX recordings_error_retry_idx
    ON recordings (retry_count, updated_at)
    WHERE status = 'ERROR';


-- -------------------------------------------------------------------------------------
-- Diğer tablolar
-- -------------------------------------------------------------------------------------
CREATE INDEX cameras_department_idx ON cameras (department_id);
CREATE INDEX cameras_status_idx ON cameras (status) WHERE active = true;

CREATE INDEX camera_sessions_camera_started_idx ON camera_sessions (camera_id, started_at DESC);

CREATE INDEX restricted_zones_camera_idx ON restricted_zones (camera_id);

CREATE INDEX violation_status_history_violation_idx
    ON violation_status_history (violation_id, changed_at DESC);
CREATE INDEX violation_status_history_user_idx ON violation_status_history (changed_by);

-- user_roles birincil anahtarı (user_id, role_id) sıralıdır; role_id'den user_id'ye
-- doğru sorgular için ters yönde ayrı indeks gerekir.
CREATE INDEX user_roles_role_idx ON user_roles (role_id);
CREATE INDEX user_departments_department_idx ON user_departments (department_id);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
-- Süresi dolmuş belirteçlerin temizlenmesi için.
CREATE INDEX refresh_tokens_expires_at_idx ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;
