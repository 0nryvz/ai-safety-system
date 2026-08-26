-- =====================================================================================
-- V3 — Referans veri ve sistem varsayılanları
--
-- Sahibi: Backend 1
--
-- DİKKAT — bu dosya DEMO VERİSİ İÇERMEZ.
-- Buradaki satırlar sistemin çalışması için zorunlu referans verilerdir ve her ortamda
-- (local, test, production) bulunmalıdır. Demo kullanıcıları, örnek kameralar ve örnek
-- ihlaller db/seed/demo-seed.sql dosyasındadır ve yalnızca local profilde yüklenir.
--
-- Tüm INSERT'ler idempotenttir: migration bir kez çalışır, ancak elle tekrar
-- çalıştırıldığında da güvenlidir.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- Zorunlu roller (PRD Bölüm 7: "Roller en az Admin, İSG uzmanı ve Vardiya sorumlusu
-- olmalıdır."). Yetkilendirme kuralları Backend 2'nindir; rol kayıtlarının varlığı
-- altyapı sorumluluğudur.
-- -------------------------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
    ('ADMIN',             'Sistem yoneticisi: kullanici, kamera, bolum ve sistem ayarlari yonetimi'),
    ('ISG_EXPERT',        'ISG uzmani: tum bolumlerdeki ihlalleri gorur, inceler ve isaretler'),
    ('SHIFT_SUPERVISOR',  'Vardiya sorumlusu: yalnizca atandigi bolumlerdeki ihlalleri gorur')
ON CONFLICT (name) DO NOTHING;


-- -------------------------------------------------------------------------------------
-- Çalışma zamanı ayarları.
--
-- Değerler PRD Bölüm 7 ve 8'deki varsayılanlardan alınmıştır. Sistem yöneticisi bunları
-- yeniden derleme gerektirmeden değiştirebilir.
-- -------------------------------------------------------------------------------------
INSERT INTO system_settings (setting_key, value, value_type, description) VALUES
    ('violation.confirmation.duration.ms', '1500', 'INTEGER',
     'Ihlalin gecerli sayilmasi icin kesintisiz devam etmesi gereken sure. PRD: 1-2 saniye. Tek karelik tespit ihlal olusturmaz.'),

    ('violation.cooldown.seconds', '10', 'INTEGER',
     'Ayni ihlal kapandiktan sonra yeni kayit ve alert olusturulmayan bekleme suresi. PRD varsayilani: 10 saniye.'),

    ('gateway.ringbuffer.seconds', '8', 'INTEGER',
     'Camera Ingestion Gateway bellek tabanli halka tamponunun uzunlugu. PRD araligi: 5-10 saniye.'),

    ('recording.postroll.seconds', '3', 'INTEGER',
     'Ihlal bittikten sonra kaydin devam ettigi ek sure. PRD araligi: 2-3 saniye.'),

    ('recording.max.duration.seconds', '120', 'INTEGER',
     'Tek bir olay klibinin ust siniri. Asildiginda ardisik parca olusturulur; boylece bitis olayi hic gelmezse kayit sonsuza kadar surmez.'),

    ('recording.retention.days', '90', 'INTEGER',
     'Ihlal kliplerinin MinIO uzerinde saklanma suresi. Suresi dolan klipler cleanup gorevi ile temizlenir.'),

    ('ai.sampling.fps', '3', 'INTEGER',
     'Gateway tarafindan AI Worker icin ornekleme yapilan saniyedeki kare sayisi. PRD hedefi: en az 3 FPS.'),

    ('ai.confidence.threshold', '0.60', 'DECIMAL',
     'Bir detection sonucunun aday ihlal sayilmasi icin gereken alt confidence esigi.'),

    ('camera.offline.threshold.seconds', '15', 'INTEGER',
     'Bu sure boyunca kare gelmeyen kamera cevrim disi kabul edilir.')
ON CONFLICT (setting_key) DO NOTHING;
