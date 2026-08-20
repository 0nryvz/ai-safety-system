import { useMemo } from 'react'
import AppShell from '../app/AppShell'
import { getDashboardErrorContent } from '../features/dashboard/dashboardErrorContent'
import { useDashboardData } from '../features/dashboard/useDashboardData'
import { useAuthSession } from '../features/auth/useAuthSession'
import Button from '../shared/ui/Button/Button'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import '../App.css'
import CameraStatusCard from '../features/dashboard/CameraStatusCard'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import { useRealtimeViolations } from '../core/realtime/useRealtimeViolations'
import { mergeDashboardViolations } from '../features/dashboard/dashboardViolationModel'
import ViolationCard from '../features/dashboard/ViolationCard'
import { hasRouteAccess } from '../features/auth/roleAccess'
import DashboardAnalyticsPanel from '../features/dashboard/DashboardAnalyticsPanel'

function DashboardPage() {
  const { session } = useAuthSession()
  const includeSummary = hasRouteAccess('authenticated', session?.user?.roles ?? [])
  const { summary, recentViolations, cameras, isLoading, error, retry } = useDashboardData({
    includeSummary,
  })
  const { violations: realtimeViolations, dismissViolation } = useRealtimeViolations()

  const dashboardViolations = useMemo(
    () => mergeDashboardViolations(recentViolations, realtimeViolations),
    [recentViolations, realtimeViolations],
  )

  const errorContent = error ? getDashboardErrorContent(error) : null

  return (
    <AppShell>
      <section className="dashboard-page" aria-labelledby="dashboard-title">
        <header className="dashboard-page__header">
          <div>
            <h2 id="dashboard-title">Operasyon Dashboardu</h2>
            <p>Kamera ve iş güvenliği ihlallerinin güncel durumunu takip edin.</p>
          </div>
        </header>

        {isLoading && (
          <div className="dashboard-loading" role="status" aria-live="polite">
            <div className="dashboard-loading__indicator" aria-hidden="true" />
            <p>Dashboard verileri yükleniyor...</p>
          </div>
        )}

        {!isLoading && error && errorContent && (
          <ErrorState
            title={errorContent.title}
            description={errorContent.description}
            action={
              <Button type="button" onClick={retry}>
                Yeniden dene
              </Button>
            }
          />
        )}

        {!isLoading && !error && summary && (
          <section className="dashboard-summary" aria-labelledby="dashboard-summary-title">
            <h3 id="dashboard-summary-title">Genel durum</h3>

            <div className="dashboard-summary__grid">
              <article className="dashboard-summary-card">
                <span>Bugünkü ihlaller</span>
                <strong>{summary.todayViolationCount}</strong>
              </article>

              <article className="dashboard-summary-card">
                <span>Son 7 gündeki ihlaller</span>
                <strong>{summary.last7DaysViolationCount}</strong>
              </article>

              <article className="dashboard-summary-card">
                <span>En sık ihlal türü</span>
                <strong>{summary.mostFrequentViolationType ?? 'Veri yok'}</strong>
              </article>

              <article className="dashboard-summary-card">
                <span>Aktif kameralar</span>
                <strong>{summary.activeCameraCount}</strong>
              </article>

              <article className="dashboard-summary-card">
                <span>Çevrim dışı kameralar</span>
                <strong>{summary.offlineCameraCount}</strong>
              </article>

              <article className="dashboard-summary-card">
                <span>Aktif ihlaller</span>
                <strong>{summary.activeViolationCount}</strong>
              </article>
            </div>
          </section>
        )}

        {!isLoading && !error && !summary && (
          <section className="dashboard-summary-unavailable" aria-live="polite">
            <h3>Özet metrikler kullanıma hazır değil</h3>
            <p>Yetkilendirilmiş kamera ve ihlal verileri aşağıdaki bölümlerde gösterilecektir.</p>
          </section>
        )}

        {!isLoading && !error && <DashboardAnalyticsPanel />}
        {!isLoading && !error && (
          <section className="dashboard-cameras" aria-labelledby="dashboard-cameras-title">
            <header className="dashboard-section-header">
              <h3 id="dashboard-cameras-title">Kamera durumları</h3>
              <span>{cameras.length} kamera</span>
            </header>

            {cameras.length === 0 ? (
              <EmptyState
                title="Kamera bulunamadı"
                description="Erişebildiğiniz departmanlarda gösterilecek kamera bulunmuyor."
              />
            ) : (
              <div className="dashboard-cameras__grid">
                {cameras.map((camera) => (
                  <CameraStatusCard key={camera.id} camera={camera} />
                ))}
              </div>
            )}
          </section>
        )}
        {!isLoading && !error && (
          <section className="dashboard-violations" aria-labelledby="dashboard-violations-title">
            <header className="dashboard-section-header">
              <h3 id="dashboard-violations-title">Son ihlaller</h3>
              <span>{dashboardViolations.length} ihlal</span>
            </header>

            {dashboardViolations.length === 0 ? (
              <EmptyState
                title="İhlal bulunamadı"
                description="Erişebildiğiniz departmanlarda gösterilecek ihlal bulunmuyor."
              />
            ) : (
              <div className="dashboard-violations__grid">
                {dashboardViolations.map((violation) => (
                  <ViolationCard
                    key={violation.violationId}
                    violation={violation}
                    onDismiss={dismissViolation}
                  />
                ))}
              </div>
            )}
          </section>
        )}
      </section>
    </AppShell>
  )
}

export default DashboardPage
