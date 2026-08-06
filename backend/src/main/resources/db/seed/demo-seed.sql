-- =====================================================================================
-- Demo / geliştirme verisi
--
-- Sahibi: Backend 1
--
-- BU DOSYA BİR FLYWAY MIGRATION'I DEĞİLDİR ve db/migration altında bulunmaz.
-- Yalnızca local profilde, app.seed.enabled=true iken DemoDataSeeder tarafından yüklenir.
-- Gerekçe (BE-1 görev planı ADIM 6): "Seed verisini production migration içine koymayın."
-- Demo verisi bir kez production şema geçmişine girerse geri alınması migration
-- gerektirir ve gerçek veriyle karışma riski taşır.
--
-- Tüm satırlar sabit UUID'ler ve ON CONFLICT DO NOTHING ile idempotenttir: uygulama
-- kaç kez başlatılırsa başlatılsın veri çoğalmaz.
--
-- Kullanıcı şifreleri buraya YAZILMAZ. DemoDataSeeder, aşağıdaki SEED_PLACEHOLDER
-- değerini uygulama başlarken BCrypt ile hash'lenmiş gerçek değerle değiştirir;
-- böylece kaynak kodda hiçbir şifre hash'i sabitlenmemiş olur.
-- =====================================================================================


-- --- Bölümler ---------------------------------------------------------------------
INSERT INTO departments (id, code, name, description) VALUES
                                                          ('11111111-0000-4000-8000-000000000001', 'KAYNAK-1', 'Kaynak Hatti 1', 'Ana govde kaynak hatti'),
                                                          ('11111111-0000-4000-8000-000000000002', 'KAYNAK-2', 'Kaynak Hatti 2', 'Sasi kaynak hatti')
    ON CONFLICT (id) DO NOTHING;


-- --- Kullanıcılar -----------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, active) VALUES
                                                                    ('22222222-0000-4000-8000-000000000001', 'admin@isgvision.local',       'SEED_PLACEHOLDER', 'Sistem Yoneticisi', true),
                                                                    ('22222222-0000-4000-8000-000000000002', 'isg.uzmani@isgvision.local',  'SEED_PLACEHOLDER', 'ISG Uzmani',        true),
                                                                    ('22222222-0000-4000-8000-000000000003', 'vardiya@isgvision.local',     'SEED_PLACEHOLDER', 'Vardiya Sorumlusu', true),
                                                                    ('22222222-0000-4000-8000-000000000004', 'pasif.kullanici@isgvision.local', 'SEED_PLACEHOLDER', 'Pasif Kullanici', false)
    ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES
          ('22222222-0000-4000-8000-000000000001'::uuid, 'ADMIN'),
          ('22222222-0000-4000-8000-000000000002'::uuid, 'ISG_EXPERT'),
          ('22222222-0000-4000-8000-000000000003'::uuid, 'SHIFT_SUPERVISOR')
     ) AS u(id, role_name)
         JOIN roles r ON r.name = u.role_name
    ON CONFLICT (user_id, role_id) DO NOTHING;
-- Vardiya sorumlusu YALNIZCA Kaynak Hatti 1'e atanmistir.
-- Dashboard yetki filtresinin dogru calistigi bu kurulumla gozle de dogrulanabilir:
-- bu kullanici Kaynak Hatti 2'deki ihlalleri hicbir ekranda gormemelidir.
INSERT INTO user_departments (user_id, department_id) VALUES
                                                          ('22222222-0000-4000-8000-000000000002', '11111111-0000-4000-8000-000000000001'),
                                                          ('22222222-0000-4000-8000-000000000002', '11111111-0000-4000-8000-000000000002'),
                                                          ('22222222-0000-4000-8000-000000000003', '11111111-0000-4000-8000-000000000001')
    ON CONFLICT (user_id, department_id) DO NOTHING;

-- --- Kameralar --------------------------------------------------------------------
INSERT INTO cameras
(id, name, code, department_id, location_description, status, active, last_seen_at) VALUES
                                                                                        ('33333333-0000-4000-8000-000000000001',
                                                                                         'Kaynak-1 Kamera A',
                                                                                         'CAM-WELDING-001',
                                                                                         '11111111-0000-4000-8000-000000000001',
                                                                                         'Kaynak hatti 1 - istasyon 3 karsisi',
                                                                                         'ONLINE',
                                                                                         true,
                                                                                         now() - interval '10 seconds'),

                                                                                        ('33333333-0000-4000-8000-000000000002',
                                                                                         'Kaynak-2 Kamera B',
                                                                                         'CAM-WELDING-002',
                                                                                         '11111111-0000-4000-8000-000000000002',
                                                                                         'Kaynak hatti 2 - robot hucresi girisi',
                                                                                         'OFFLINE',
                                                                                         true,
                                                                                         now() - interval '2 hours'),

                                                                                        ('33333333-0000-4000-8000-000000000003',
                                                                                         'Kaynak-1 Kamera C',
                                                                                         'CAM-WELDING-003',
                                                                                         '11111111-0000-4000-8000-000000000001',
                                                                                         'Kaynak hatti 1 - malzeme girisi',
                                                                                         'DEGRADED',
                                                                                         true,
                                                                                         now() - interval '40 seconds')
    ON CONFLICT (id) DO NOTHING;
-- --- Yasaklı alan -----------------------------------------------------------------
-- Koordinatlar referans görüntüye göre normalize edilmiştir ([0,1] aralığı).
INSERT INTO restricted_zones (id, camera_id, name, polygon) VALUES
    ('44444444-0000-4000-8000-000000000001',
     '33333333-0000-4000-8000-000000000001',
     'Kaynak Yasakli Alan',
     '[{"x": 0.10, "y": 0.35},
       {"x": 0.62, "y": 0.35},
       {"x": 0.62, "y": 0.92},
       {"x": 0.10, "y": 0.92}]'::jsonb)
    ON CONFLICT (id) DO NOTHING;

-- --- Kamera oturumları ------------------------------------------------------------
INSERT INTO camera_sessions (id, camera_id, status, started_at, ended_at, last_frame_at, client_info) VALUES
                                                                                                          ('55555555-0000-4000-8000-000000000001', '33333333-0000-4000-8000-000000000001', 'ACTIVE',
                                                                                                           now() - interval '35 minutes', NULL, now() - interval '10 seconds', 'Flutter demo / Android 14'),
                                                                                                          ('55555555-0000-4000-8000-000000000002', '33333333-0000-4000-8000-000000000002', 'TIMED_OUT',
                                                                                                           now() - interval '4 hours', now() - interval '2 hours', now() - interval '2 hours', 'Flutter demo / Android 13')
    ON CONFLICT (id) DO NOTHING;


-- --- İhlaller ---------------------------------------------------------------------
-- Kapsam bilinçli olarak çeşitlidir: farklı türler, farklı bölümler, son 7 güne yayılmış
-- tarihler ve tüm lifecycle/review kombinasyonları. Dashboard'un boş olmayan veriyle
-- doğrulanabilmesi için gereklidir.
--
-- DİKKAT: Kısmi unique indeks nedeniyle bir kamera + ihlal türü için yalnızca TEK bir
-- ACTIVE ihlal eklenebilir. Aşağıda bilinçli olarak tek bir aktif ihlal vardır.
INSERT INTO violations (
    id, camera_id, department_id, camera_session_id, restricted_zone_id,
    violation_type, started_at, ended_at, confidence, model_version,
    lifecycle_status, review_status, cover_image_key,
    detected_at, alert_sent_at, reviewed_by, reviewed_at
) VALUES
      -- 1) Su anda devam eden ihlal (tek ACTIVE kayit)
      ('66666666-0000-4000-8000-000000000001', '33333333-0000-4000-8000-000000000001',
       '11111111-0000-4000-8000-000000000001', '55555555-0000-4000-8000-000000000001', NULL,
       'MISSING_WELDING_MASK', now() - interval '40 seconds', NULL, 0.9120, 'yolo-v8n-isg-0.3.1',
       'ACTIVE', 'UNREVIEWED', 'covers/2026/66666666-0001.jpg',
       now() - interval '41 seconds', now() - interval '40 seconds', NULL, NULL),

      -- 2) Tamamlanmis, klibi hazir, henuz incelenmemis
      ('66666666-0000-4000-8000-000000000002', '33333333-0000-4000-8000-000000000001',
       '11111111-0000-4000-8000-000000000001', '55555555-0000-4000-8000-000000000001', NULL,
       'MISSING_WELDING_JACKET', now() - interval '3 hours', now() - interval '3 hours' + interval '9 seconds',
       0.8740, 'yolo-v8n-isg-0.3.1', 'COMPLETED', 'UNREVIEWED', 'covers/2026/66666666-0002.jpg',
       now() - interval '3 hours' - interval '1 second', now() - interval '3 hours' + interval '1 second', NULL, NULL),

      -- 3) Restricted zone ihlali - kullanici tarafindan dogrulanmis
      ('66666666-0000-4000-8000-000000000003', '33333333-0000-4000-8000-000000000001',
       '11111111-0000-4000-8000-000000000001', NULL, '44444444-0000-4000-8000-000000000001',
       'RESTRICTED_ZONE', now() - interval '1 day', now() - interval '1 day' + interval '14 seconds',
       0.9450, 'yolo-v8n-isg-0.3.1', 'COMPLETED', 'CONFIRMED', 'covers/2026/66666666-0003.jpg',
       now() - interval '1 day' - interval '1 second', now() - interval '1 day' + interval '1 second',
       '22222222-0000-4000-8000-000000000002', now() - interval '22 hours'),

      -- 4) YANLIS ALARM olarak isaretlenmis (dusuk confidence)
      ('66666666-0000-4000-8000-000000000004', '33333333-0000-4000-8000-000000000003',
       '11111111-0000-4000-8000-000000000001', NULL, NULL,
       'MISSING_GLOVES', now() - interval '2 days', now() - interval '2 days' + interval '4 seconds',
       0.6310, 'yolo-v8n-isg-0.3.0', 'COMPLETED', 'FALSE_ALARM', 'covers/2026/66666666-0004.jpg',
       now() - interval '2 days', now() - interval '2 days' + interval '1 second',
       '22222222-0000-4000-8000-000000000002', now() - interval '2 days' + interval '30 minutes'),

      -- 5) Klip hazirlaniyor (PREPARING) — web panelinde "isleniyor" gosterilecek
      ('66666666-0000-4000-8000-000000000005', '33333333-0000-4000-8000-000000000001',
       '11111111-0000-4000-8000-000000000001', '55555555-0000-4000-8000-000000000001', NULL,
       'MISSING_WELDING_APRON', now() - interval '12 minutes', now() - interval '11 minutes',
       0.8020, 'yolo-v8n-isg-0.3.1', 'PREPARING', 'UNREVIEWED', 'covers/2026/66666666-0005.jpg',
       now() - interval '12 minutes', now() - interval '12 minutes' + interval '1 second', NULL, NULL),

      -- 6) Kayit HATASI alan ihlal — recording basari orani KPI'sini test eder
      ('66666666-0000-4000-8000-000000000006', '33333333-0000-4000-8000-000000000002',
       '11111111-0000-4000-8000-000000000002', '55555555-0000-4000-8000-000000000002', NULL,
       'MISSING_WELDING_JACKET', now() - interval '3 days',
       now() - interval '3 days' + interval '7 seconds', 0.8890, 'yolo-v8n-isg-0.3.0',
       'ERROR', 'UNREVIEWED', NULL,
       now() - interval '3 days', now() - interval '3 days' + interval '1 second', NULL, NULL),

      -- 7-10) Trend grafiginin anlamli gorunmesi icin gecmis gunlere yayilmis kayitlar
      ('66666666-0000-4000-8000-000000000007', '33333333-0000-4000-8000-000000000002',
       '11111111-0000-4000-8000-000000000002', NULL, NULL,
       'MISSING_WELDING_MASK', now() - interval '4 days', now() - interval '4 days' + interval '11 seconds',
       0.9010, 'yolo-v8n-isg-0.3.0', 'COMPLETED', 'REVIEWED', 'covers/2026/66666666-0007.jpg',
       now() - interval '4 days', now() - interval '4 days' + interval '1 second',
       '22222222-0000-4000-8000-000000000002', now() - interval '4 days' + interval '1 hour'),

      ('66666666-0000-4000-8000-000000000008', '33333333-0000-4000-8000-000000000001',
       '11111111-0000-4000-8000-000000000001', NULL, NULL,
       'MISSING_WELDING_MASK', now() - interval '5 days', now() - interval '5 days' + interval '6 seconds',
       0.8550, 'yolo-v8n-isg-0.3.0', 'COMPLETED', 'CONFIRMED', 'covers/2026/66666666-0008.jpg',
       now() - interval '5 days', now() - interval '5 days' + interval '1 second',
       '22222222-0000-4000-8000-000000000002', now() - interval '5 days' + interval '2 hours'),

      ('66666666-0000-4000-8000-000000000009', '33333333-0000-4000-8000-000000000003',
       '11111111-0000-4000-8000-000000000001', NULL, NULL,
       'MISSING_WELDING_JACKET', now() - interval '6 days', now() - interval '6 days' + interval '8 seconds',
       0.7930, 'yolo-v8n-isg-0.3.0', 'COMPLETED', 'UNREVIEWED', 'covers/2026/66666666-0009.jpg',
       now() - interval '6 days', now() - interval '6 days' + interval '1 second', NULL, NULL),

      ('66666666-0000-4000-8000-000000000010', '33333333-0000-4000-8000-000000000002',
       '11111111-0000-4000-8000-000000000002', NULL, NULL,
       'MISSING_GLOVES', now() - interval '6 days', now() - interval '6 days' + interval '5 seconds',
       0.7120, 'yolo-v8n-isg-0.3.0', 'COMPLETED', 'REVIEWED', 'covers/2026/66666666-0010.jpg',
       now() - interval '6 days', now() - interval '6 days' + interval '1 second',
       '22222222-0000-4000-8000-000000000002', now() - interval '6 days' + interval '3 hours')
    ON CONFLICT (id) DO NOTHING;
-- --- Kayıtlar (klipler) -----------------------------------------------------------
INSERT INTO recordings (
    id, violation_id, status, object_key, duration_ms, size_bytes,
    retry_count, checksum, error_code, recording_started_at, ready_at
) VALUES
      -- Devam eden ihlalin kaydi da devam ediyor
      ('77777777-0000-4000-8000-000000000001', '66666666-0000-4000-8000-000000000001', 'RECORDING',
       NULL, NULL, NULL, 0, NULL, NULL, now() - interval '39 seconds', NULL),

      ('77777777-0000-4000-8000-000000000002', '66666666-0000-4000-8000-000000000002', 'READY',
       'clips/2026/66666666-0002.mp4', 17000, 2148000, 0, 'sha256:a1b2c3d4e5f6', NULL,
       now() - interval '3 hours', now() - interval '3 hours' + interval '13 seconds'),

      ('77777777-0000-4000-8000-000000000003', '66666666-0000-4000-8000-000000000003', 'READY',
       'clips/2026/66666666-0003.mp4', 22000, 2890000, 0, 'sha256:b2c3d4e5f6a1', NULL,
       now() - interval '1 day', now() - interval '1 day' + interval '18 seconds'),

      ('77777777-0000-4000-8000-000000000004', '66666666-0000-4000-8000-000000000004', 'READY',
       'clips/2026/66666666-0004.mp4', 12000, 1420000, 0, 'sha256:c3d4e5f6a1b2', NULL,
       now() - interval '2 days', now() - interval '2 days' + interval '9 seconds'),

      -- Klibi hazirlanmakta olan ihlal
      ('77777777-0000-4000-8000-000000000005', '66666666-0000-4000-8000-000000000005', 'PROCESSING',
       NULL, NULL, NULL, 0, NULL, NULL, now() - interval '12 minutes', NULL),

      -- MinIO yukleme hatasi ve yeniden deneme
      ('77777777-0000-4000-8000-000000000006', '66666666-0000-4000-8000-000000000006', 'ERROR',
       NULL, NULL, NULL, 3, NULL, 'MINIO_UPLOAD_FAILED', now() - interval '3 days', NULL),

      ('77777777-0000-4000-8000-000000000007', '66666666-0000-4000-8000-000000000007', 'READY',
       'clips/2026/66666666-0007.mp4', 19000, 2410000, 1, 'sha256:d4e5f6a1b2c3', NULL,
       now() - interval '4 days', now() - interval '4 days' + interval '16 seconds'),

      ('77777777-0000-4000-8000-000000000008', '66666666-0000-4000-8000-000000000008', 'READY',
       'clips/2026/66666666-0008.mp4', 14000, 1780000, 0, 'sha256:e5f6a1b2c3d4', NULL,
       now() - interval '5 days', now() - interval '5 days' + interval '11 seconds'),

      ('77777777-0000-4000-8000-000000000009', '66666666-0000-4000-8000-000000000009', 'READY',
       'clips/2026/66666666-0009.mp4', 16000, 2010000, 0, 'sha256:f6a1b2c3d4e5', NULL,
       now() - interval '6 days', now() - interval '6 days' + interval '12 seconds'),

      ('77777777-0000-4000-8000-000000000010', '66666666-0000-4000-8000-000000000010', 'READY',
       'clips/2026/66666666-0010.mp4', 13000, 1650000, 0, 'sha256:a1c3e5b2d4f6', NULL,
       now() - interval '6 days', now() - interval '6 days' + interval '10 seconds')
    ON CONFLICT (id) DO NOTHING;


-- --- Durum geçmişi örneği ---------------------------------------------------------
INSERT INTO violation_status_history (id, violation_id, status_kind, from_status, to_status, changed_by, changed_at, note) VALUES
                                                                                                                               ('88888888-0000-4000-8000-000000000001', '66666666-0000-4000-8000-000000000003', 'LIFECYCLE',
                                                                                                                                'ACTIVE', 'PREPARING', NULL, now() - interval '1 day' + interval '14 seconds', 'Ihlal sona erdi, kayit kapatiliyor'),
                                                                                                                               ('88888888-0000-4000-8000-000000000002', '66666666-0000-4000-8000-000000000003', 'LIFECYCLE',
                                                                                                                                'PREPARING', 'COMPLETED', NULL, now() - interval '1 day' + interval '18 seconds', 'Klip MinIO uzerine yuklendi'),
                                                                                                                               ('88888888-0000-4000-8000-000000000003', '66666666-0000-4000-8000-000000000003', 'REVIEW',
                                                                                                                                'UNREVIEWED', 'CONFIRMED', '22222222-0000-4000-8000-000000000002', now() - interval '22 hours',
                                                                                                                                'Video kanitiyla dogrulandi')
    ON CONFLICT (id) DO NOTHING;
