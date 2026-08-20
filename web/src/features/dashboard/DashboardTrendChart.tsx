import type { DashboardTrendPoint } from './dashboardTypes'
import './DashboardTrendChart.css'

interface DashboardTrendChartProps {
  data: DashboardTrendPoint[]
}

const CHART_WIDTH = 640
const CHART_HEIGHT = 240
const CHART_PADDING = 32

function formatDate(value: string): string {
  const [year, month, day] = value.split('-')

  if (!year || !month || !day) {
    return value
  }

  return `${day}.${month}`
}

function DashboardTrendChart({ data }: DashboardTrendChartProps) {
  if (data.length === 0) {
    return (
      <div className="dashboard-trend-chart__empty">
        <p>Seçilen tarih aralığında trend verisi bulunmuyor.</p>
      </div>
    )
  }

  const maxCount = Math.max(...data.map((point) => point.count), 1)

  const plotWidth = CHART_WIDTH - CHART_PADDING * 2
  const plotHeight = CHART_HEIGHT - CHART_PADDING * 2

  const points = data.map((point, index) => {
    const x =
      data.length === 1 ? CHART_WIDTH / 2 : CHART_PADDING + (index / (data.length - 1)) * plotWidth

    const y = CHART_PADDING + plotHeight - (point.count / maxCount) * plotHeight

    return {
      ...point,
      x,
      y,
    }
  })

  const polylinePoints = points.map((point) => `${point.x},${point.y}`).join(' ')

  return (
    <div
      className="dashboard-trend-chart"
      role="img"
      aria-label="Günlük ihlal sayısı trend grafiği"
    >
      <svg
        className="dashboard-trend-chart__svg"
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        aria-hidden="true"
      >
        <line
          className="dashboard-trend-chart__axis"
          x1={CHART_PADDING}
          y1={CHART_HEIGHT - CHART_PADDING}
          x2={CHART_WIDTH - CHART_PADDING}
          y2={CHART_HEIGHT - CHART_PADDING}
        />

        <polyline className="dashboard-trend-chart__line" points={polylinePoints} />

        {points.map((point) => (
          <circle
            key={point.date}
            className="dashboard-trend-chart__point"
            cx={point.x}
            cy={point.y}
            r="5"
          />
        ))}
      </svg>

      <div className="dashboard-trend-chart__labels">
        {data.map((point) => (
          <div key={point.date} className="dashboard-trend-chart__label">
            <strong>{point.count}</strong>
            <span>{formatDate(point.date)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default DashboardTrendChart
