import { formatUtcToLocal } from '../../core/date/dateTime'
import StatusBadge from '../../shared/ui/StatusBadge/StatusBadge'
import { getCameraStatusPresentation } from './cameraStatusPresentation'
import type { Camera } from './dashboardTypes'
import './CameraStatusCard.css'

interface CameraStatusCardProps {
  camera: Camera
}

function CameraStatusCard({ camera }: CameraStatusCardProps) {
  const statusPresentation = getCameraStatusPresentation(camera.connectionStatus)
  const titleId = `camera-${camera.id}-title`

  return (
    <article className="camera-status-card" aria-labelledby={titleId}>
      <header className="camera-status-card__header">
        <div>
          <h4 id={titleId}>{camera.name}</h4>
          <p>{camera.code}</p>
        </div>

        <StatusBadge variant={statusPresentation.variant}>{statusPresentation.label}</StatusBadge>
      </header>

      <dl className="camera-status-card__details">
        <div>
          <dt>Kamera kullanımı</dt>
          <dd>{camera.active ? 'Aktif' : 'Pasif'}</dd>
        </div>

        <div>
          <dt>Son görülme</dt>
          <dd>{camera.lastSeenAt ? formatUtcToLocal(camera.lastSeenAt) : 'Henüz görülmedi'}</dd>
        </div>
      </dl>
    </article>
  )
}

export default CameraStatusCard
