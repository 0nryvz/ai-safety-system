# AI Safety System — Web Dashboard

React, TypeScript ve Vite ile geliştirilen gerçek zamanlı güvenlik izleme panelidir.

## Gereksinimler

- Node.js
- npm

## Kurulum

```bash
npm install
```

## Geliştirme

```bash
npm run dev
```

## Doğrulama

```bash
npm run lint
npm run build
```

## Kaynak Yapısı

```text
src/
├── app/          # Uygulama seviyesindeki bileşenler
├── components/   # Tekrar kullanılabilir arayüz bileşenleri
├── pages/        # Sayfa bileşenleri
├── App.tsx       # Oturum durumuna göre ana ekran seçimi
└── main.tsx      # React uygulamasının başlangıç noktası
```

## Mevcut Uygulama Altyapısı

- Responsive `AppShell`
- Header, sidebar ve ana içerik alanı
- Oturum durumuna göre giriş ve dashboard ekranları
- Uygulama seviyesinde `ErrorBoundary`
- Beklenmeyen render hataları için kullanıcı geri bildirimi

## Kod Doğrulama

Değişiklik göndermeden önce aşağıdaki kontroller çalıştırılmalıdır:

```bash
npm run lint
npm run build
```

Feature sahipliği ve route sözleşmesi, frontend geliştiricileri tarafından ortak kararlaştırıldıktan sonra bu belgeye eklenecektir.
