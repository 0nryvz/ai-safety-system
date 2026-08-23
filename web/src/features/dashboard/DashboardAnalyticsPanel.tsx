import { useMemo, useState } from 'react'
import Button from '../../shared/ui/Button/Button'
import DateRange from '../../shared/ui/DateRange/DateRange'
import ErrorState from '../../shared/ui/ErrorState/ErrorState'
import Select from '../../shared/ui/Select/Select'
import DashboardDistributionChart from './DashboardDistributionChart'
import DashboardTrendChart from './DashboardTrendChart'
import type { DashboardDistributionGroup } from './dashboardTypes'
import { useDashboardAnalytics } from './useDashboardAnalytics'
import './DashboardAnalyticsPanel.css'
import { getViolationTypeLabel } from './violationPresentation'

const DEFAULT_RANGE_DAYS = 6

function formatDateInput(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function createDefaultDateRange() {
  const to = new Date()
  const from = new Date(to)

  from.setUTCDate(from.getUTCDate() - DEFAULT_RANGE_DAYS)

  return {
    from: formatDateInput(from),
    to: formatDateInput(to),
  }
}

function DashboardAnalyticsPanel() {
  const initialRange = useMemo(() => createDefaultDateRange(), [])
  const [from, setFrom] = useState(initialRange.from)
  const [to, setTo] = useState(initialRange.to)
  const [groupBy, setGroupBy] = useState<DashboardDistributionGroup>('TYPE')

  const { trend, distribution, isLoading, error, retry } = useDashboardAnalytics({
    from,
    to,
    groupBy,
  })

  return (
    <section className="dashboard-analytics" aria-labelledby="dashboard-analytics-title">
      <header className="dashboard-section-header">
        <div>
          <h3 id="dashboard-analytics-title">Analitik görünüm</h3>
          <p>İhlal eğilimlerini ve dağılımlarını inceleyin.</p>
        </div>
      </header>

      <div className="dashboard-analytics__filters">
        <DateRange
          startDate={from}
          endDate={to}
          onStartDateChange={setFrom}
          onEndDateChange={setTo}
          disabled={isLoading}
        />

        <Select
          label="Dağılım türü"
          value={groupBy}
          disabled={isLoading}
          onChange={(event) => setGroupBy(event.target.value as DashboardDistributionGroup)}
        >
          <option value="TYPE">İhlal türü</option>
          <option value="CAMERA">Kamera</option>
          <option value="DEPARTMENT">Departman</option>
        </Select>
      </div>

      {isLoading && (
        <div className="dashboard-analytics__loading" role="status" aria-live="polite">
          Analitik veriler yükleniyor...
        </div>
      )}

      {!isLoading && error && (
        <ErrorState
          title="Analitik veriler yüklenemedi"
          description="Trend ve dağılım verileri alınırken bir hata oluştu."
          action={
            <Button type="button" onClick={retry}>
              Yeniden dene
            </Button>
          }
        />
      )}

      {!isLoading && !error && (
        <div className="dashboard-analytics__grid">
          <article className="dashboard-analytics-card">
            <header>
              <h4>Günlük ihlal trendi</h4>
              <p>Seçilen tarih aralığındaki günlük ihlal sayıları.</p>
            </header>

            <DashboardTrendChart data={trend} />
          </article>

          <article className="dashboard-analytics-card">
            <header>
              <h4>İhlal dağılımı</h4>
              <p>Seçilen gruplamaya göre toplam ihlal dağılımı.</p>
            </header>

            <DashboardDistributionChart
              data={
                groupBy === 'TYPE'
                  ? distribution.map((item) => ({
                      ...item,
                      group: getViolationTypeLabel(item.group),
                    }))
                  : distribution
              }
            />
          </article>
        </div>
      )}
    </section>
  )
}

export default DashboardAnalyticsPanel
