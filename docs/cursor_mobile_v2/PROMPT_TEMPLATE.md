# Cursor Task Prompt Template

Her yeni Cursor chat/agent çalışmasında bunu kopyala ve görev dosyasını değiştir.

```text
@00_SHARED_CONTEXT.md
@01_CONTRACT_SNAPSHOT.md
@<TASK_FILE>.md

Güncel repo kodu contract için source of truth.

Çalışma şekli:
1. Önce TASK_FILE içindeki READ FIRST dosyalarını oku.
2. Repo genelinde geniş arama yapma.
3. Yalnız gerekliyse hedefli grep/search yap.
4. Önce kısa implementation planı ver.
5. Benden ek onay istemeden plan uygunsa kodu uygula.
6. WRITE SCOPE dışına yazma.
7. Backend/Gateway/AI/Web koduna dokunma.
8. Endpoint, enum, DTO alanı eksikse uydurma; STOP CONDITION olarak bildir.
9. Var olan kamera/session/streaming kodunu görev istemiyorsa refactor etme.
10. Her mantıksal değişiklik sonrası en dar ilgili testi çalıştır.
11. Görev sonunda:
   - değişen dosyalar
   - contractlar
   - test sonucu
   - kalan risk
   formatında kısa rapor ver.
```

## Hata çözümünde token tasarrufu

İlk hata turunda Cursor'a sadece:
- komut
- hata mesajının ilgili 50-100 satırı
- değişen dosyalar

ver.

Tüm terminal logunu veya tüm repoyu tekrar context'e koyma.
