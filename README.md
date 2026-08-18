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
