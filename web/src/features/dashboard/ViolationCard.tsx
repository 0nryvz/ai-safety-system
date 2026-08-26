import { formatUtcToLocal } from '../../core/date/dateTime'
import Button from '../../shared/ui/Button/Button'
import StatusBadge from '../../shared/ui/StatusBadge/StatusBadge'
import type { DashboardViolation } from './dashboardViolationModel'
import {
  getLifecycleStatusPresentation,
  getRecordingStatusPresentation,
  getViolationTypeLabel,
} from './violationPresentation'
import './ViolationCard.css'

interface ViolationCardProps {
  violation: DashboardViolation
  onDismiss?: (violationId: string) => void
}

function ViolationCard({ violation, onDismiss }: ViolationCardProps) {
  const lifecyclePresentation = getLifecycleStatusPresentation(violation.lifecycleStatus)
  const recordingPresentation = getRecordingStatusPresentation(violation.recordingStatus)
  const titleId = `violation-${violation.violationId}-title`

  return (
    <article className="violation-card" aria-labelledby={titleId}>
      <header className="violation-card__header">
        <h4 id={titleId}>{getViolationTypeLabel(violation.violationType)}</h4>

        <StatusBadge variant={lifecyclePresentation.variant}>
          {lifecyclePresentation.label}
        </StatusBadge>
      </header>

      <dl className="violation-card__details">
        <div>
          <dt>Kamera</dt>
          <dd>{violation.cameraName ?? 'Kamera bilgisi yok'}</dd>
        </div>

        <div>
          <dt>Bölüm</dt>
          <dd>{violation.departmentName ?? 'Bölüm bilgisi bekleniyor'}</dd>
        </div>

        <div>
          <dt>Olay zamanı</dt>
          <dd>
            {violation.occurredAt ? formatUtcToLocal(violation.occurredAt) : 'Zaman bilgisi yok'}
          </dd>
        </div>

        <div>
          <dt>Güven oranı</dt>
          <dd>
            {violation.confidence === null
              ? 'Veri yok'
              : `%${Math.round(violation.confidence * 100)}`}
          </dd>
        </div>
      </dl>

      <footer className="violation-card__footer">
        <div className="violation-card__recording">
          <span>Kayıt durumu</span>
          <StatusBadge variant={recordingPresentation.variant}>
            {recordingPresentation.label}
          </StatusBadge>
        </div>

        {violation.source === 'REALTIME' && onDismiss && (
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              onDismiss(violation.violationId)
            }}
          >
            Uyarıyı kapat
          </Button>
        )}
      </footer>
    </article>
  )
}

export default ViolationCard
