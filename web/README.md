# AI Safety System — Web Dashboard

React, TypeScript ve Vite ile geliştirilen gerçek zamanlı güvenlik izleme panelidir.

## Gereksinimler

- Node.js
- npm

## Kurulum

```bash
npm install
```

## Environment Yapılandırması

Projede kullanılabilen environment değişkenleri `.env.example` dosyasında belgelenmiştir.

Yerel geliştirme ayarları için örnek dosyayı `.env.local` adıyla kopyalayabilirsiniz:

```bash
cp .env.example .env.local
```

Kullanılabilen değişkenler:

| Değişken                    | Varsayılan değer | Açıklama                            |
| --------------------------- | ---------------- | ----------------------------------- |
| `VITE_API_BASE_URL`         | `/api/v1`        | Backend API adresi                  |
| `VITE_WEBSOCKET_URL`        | Boş              | WebSocket bağlantı adresi           |
| `VITE_ENABLE_MOCK_DATA`     | `false`          | Mock veri kullanımını etkinleştirir |
| `VITE_ENABLE_DEBUG_LOGGING` | `false`          | Geliştirme loglarını etkinleştirir  |

Gerçek parola, erişim anahtarı veya token gibi gizli bilgiler `.env.example` dosyasına eklenmemelidir.

Environment değişkenlerine uygulama içinde doğrudan erişmek yerine `src/config/env.ts` kullanılmalıdır.

## Geliştirme

```bash
npm run dev
```

## Route Sözleşmesi

Route adresleri ve sahiplik bilgileri `src/app/routeConfig.ts` dosyasında merkezi olarak tutulur.

| Route        | Erişim        | Sahip | Açıklama                                                |
| ------------ | ------------- | ----- | ------------------------------------------------------- |
| `/`          | Yönlendirme   | FE1   | Oturum durumuna göre login veya dashboard’a yönlendirir |
| `/login`     | Public        | FE2   | Kullanıcı giriş sayfası                                 |
| `/dashboard` | Authenticated | FE1   | Korumalı dashboard sayfası                              |
| `*`          | Public        | FE1   | Bilinmeyen adresler için 404 sayfası                    |

Yeni bir route eklenirken adres, sahiplik ve erişim türü `appRouteConfig` sözleşmesine eklenmelidir.

## Kimlik Doğrulama Entegrasyonu

`src/app/RequireAuth.tsx`, korumalı route’lar için ortak entegrasyon noktasıdır.

- `sessionStorage` içinde `accessToken` yoksa kullanıcı `/login` adresine yönlendirilir.
- Kullanıcının ulaşmak istediği adres yönlendirme sırasında korunur.
- Token varsa korumalı alt route görüntülenir.
- FE2 tarafından geliştirilecek yeni korumalı sayfalar aynı guard altında tanımlanabilir.

## Feature Flag Yapısı

Feature flag’ler `src/config/featureFlags.ts` dosyasında merkezi olarak yönetilir.

Uygulama içinde flag kontrolü şu şekilde yapılabilir:

```ts
import { isFeatureEnabled } from './config/featureFlags'

if (isFeatureEnabled('mockData')) {
  // Mock veri davranışı
}
```

Şu anda desteklenen flag’ler:

- `mockData`
- `debugLogging`

## Kaynak Yapısı

```text
src/
├── app/             # AppShell, route sözleşmesi ve route guard
├── components/      # Tekrar kullanılabilir arayüz bileşenleri
├── config/          # Environment ve feature flag yapılandırması
├── pages/           # Login, dashboard ve 404 sayfaları
├── services/        # API ve kimlik doğrulama servisleri
├── App.tsx          # Uygulama route tanımları
└── main.tsx         # React uygulamasının başlangıç noktası
```

## Mevcut Uygulama Altyapısı

- Responsive `AppShell`
- Header, sidebar ve ana içerik alanı
- React Router tabanlı yönlendirme
- Public ve authenticated route ayrımı
- FE2 route guard entegrasyon noktası
- Bilinmeyen adresler için 404 sayfası
- Merkezi environment yapılandırması
- Feature flag altyapısı
- Uygulama seviyesinde `ErrorBoundary`
- Beklenmeyen render hataları için kullanıcı geri bildirimi
- Canlı video veya stream bileşeni içermez

## Kod Doğrulama

Değişiklik göndermeden önce aşağıdaki kontroller çalıştırılmalıdır:

```bash
npm run build
npm run lint
npm run format:check
npm run test
```
