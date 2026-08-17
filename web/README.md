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

Yerel geliştirmede frontend ve backend farklı portlarda çalışıyorsa `.env.local` içinde tam WebSocket adresi verilmelidir:

```env
VITE_WEBSOCKET_URL=ws://localhost:8080/ws
```

Production ortamında frontend ve backend aynı origin üzerinden sunuluyorsa değişken boş bırakılabilir. Uygulama bağlantıyı otomatik olarak `ws://<host>/ws` veya HTTPS altında `wss://<host>/ws` şeklinde oluşturur.

Kullanılabilen değişkenler:

| Değişken                    | Varsayılan değer | Açıklama                                                          |
| --------------------------- | ---------------- | ----------------------------------------------------------------- |
| `VITE_API_BASE_URL`         | `/api/v1`        | Backend API adresi                                                |
| `VITE_WEBSOCKET_URL`        | Boş              | WebSocket adresi; boşsa mevcut origin üzerindeki `/ws` kullanılır |
| `VITE_ENABLE_MOCK_DATA`     | `false`          | Mock veri kullanımını etkinleştirir                               |
| `VITE_ENABLE_DEBUG_LOGGING` | `false`          | Geliştirme loglarını etkinleştirir                                |

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

- Session yaşam döngüsü merkezi `AuthTokenProvider` üzerinden yönetilir.
- Uygulama başlangıcında kayıtlı session restore edilir ve `/api/v1/users/me` ile kullanıcı bilgisi alınır.
- Token yoksa kullanıcı `/login` adresine yönlendirilir.
- Kullanıcının ulaşmak istediği adres yönlendirme sırasında korunur.
- Realtime ve diğer feature modülleri tokenı doğrudan `sessionStorage` üzerinden okumamalıdır.
- Token erişimi için `authTokenProvider.getAccessToken()` kullanılmalıdır.

## Realtime WebSocket Entegrasyonu

Realtime bağlantısı `src/core/realtime` altında merkezi olarak yönetilir.

- Protokol: Native WebSocket üzerinden STOMP
- Client: `@stomp/stompjs`
- Endpoint: `/ws`
- Subscription: `/user/queue/alerts`
- CONNECT header: `Authorization: Bearer <JWT>`
- SockJS kullanılmaz.
- JWT query string’e veya loglara yazılmaz.
- Login sonrasında bağlantı kurulur.
- Token refresh sonrasında güncel tokenla yeniden bağlantı kurulur.
- Profile update bağlantıyı yenilemez.
- Logout ve session expiry bağlantıyı kapatır.
- Bağlantı koptuğunda 1 saniyeden başlayıp 30 saniyede sınırlanan exponential backoff uygulanır.
- Reconnect sonrasında REST recovery için callback sözleşmesi hazırdır; backend recovery endpointi kesinleşene kadar sahte endpoint kullanılmaz.

Realtime transport ham mesaj aboneliğini korurken, geçerli alert ve violation update mesajları merkezi `RealtimeEventStore` içine aktarılır.

### Realtime Event Store

- Gelen JSON payload’ları kullanılmadan önce runtime validation işleminden geçirilir.
- Eksik veya geçersiz `violationId` içeren mesajlar state’e eklenmez.
- Bilinmeyen violation type, lifecycle status ve recording status değerleri uygulamayı durdurmadan `UNKNOWN` değerine dönüştürülür.
- Her violation `violationId` anahtarıyla tek bir merkezi kayıt olarak tutulur.
- İlk alert violation kaydını oluşturur; sonraki update mesajları aynı kaydı günceller.
- Update mesajları `updatedAt` değerine göre sıralanır; eski veya aynı tarihli update’ler uygulanmaz.
- Tekrarlanan event’ler deterministik event anahtarı ve süre/kapasite sınırı bulunan cache ile engellenir.
- Dismiss işlemi yalnızca istemci state’ini değiştirir ve backend violation kaydını silmez.
- Logout veya session expiry sonrasında realtime state ve duplicate geçmişi temizlenir.
- React bileşenleri merkezi state’e `useRealtimeViolations()` hook’u üzerinden abone olmalıdır.
- `VITE_ENABLE_DEBUG_LOGGING=true` olduğunda geçersiz mesajlar ve bilinmeyen enum değerleri için yalnızca güvenli diagnostic kodu yazılır; payload, bilinmeyen enum’un gerçek değeri, JWT ve STOMP header bilgileri loglanmaz.
- Reconnect sonrasında REST recovery abonelik sözleşmesi hazırdır. Backend recovery endpoint’i kesinleşmeden sahte endpoint veya DTO kullanılmaz.

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
