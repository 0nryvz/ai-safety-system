import type { DashboardDistributionItem } from './dashboardTypes'
import './DashboardDistributionChart.css'

interface DashboardDistributionChartProps {
  data: DashboardDistributionItem[]
}

function DashboardDistributionChart({ data }: DashboardDistributionChartProps) {
  if (data.length === 0) {
    return (
      <div className="dashboard-distribution-chart__empty">
        <p>Dağılım verisi bulunmuyor.</p>
      </div>
    )
  }

  const maxCount = Math.max(...data.map((item) => item.count), 1)

  return (
    <div className="dashboard-distribution-chart" role="img" aria-label="İhlal dağılım grafiği">
      {data.map((item) => {
        const widthPercentage = (item.count / maxCount) * 100

        return (
          <div key={item.group} className="dashboard-distribution-chart__row">
            <div className="dashboard-distribution-chart__meta">
              <span>{item.group}</span>
              <strong>{item.count}</strong>
            </div>

            <div className="dashboard-distribution-chart__track" aria-hidden="true">
              <div
                className="dashboard-distribution-chart__bar"
                style={{
                  width: `${widthPercentage}%`,
                }}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default DashboardDistributionChart
