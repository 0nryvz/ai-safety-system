import { Bell, Volume2, VolumeX, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ROUTE_PATHS } from '../../app/routeConfig'
import { formatUtcToLocal } from '../../core/date/dateTime'
import { useRealtimeViolations } from '../../core/realtime/useRealtimeViolations'
import Button from '../../shared/ui/Button/Button'
import './AlertCenter.css'
import { formatConfidence, getRecordingStatusLabel, getViolationTypeLabel } from './alertLabels'
import { readAlertSoundMuted, writeAlertSoundMuted } from './alertSoundPreference'
import { useAlertSound } from './useAlertSound'

function AlertCenter() {
  const navigate = useNavigate()
  const alertCenterRef = useRef<HTMLElement>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [isSoundMuted, setIsSoundMuted] = useState(readAlertSoundMuted)
  const { violations, dismissViolation } = useRealtimeViolations()
  useAlertSound(violations, isSoundMuted)

  function openViolationDetail(violationId: string) {
    if (!violationId) {
      return
    }

    setIsOpen(false)
    navigate(ROUTE_PATHS.violationDetail.replace(':id', violationId))
  }

  useEffect(() => {
    if (!isOpen) {
      return
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }

    function handlePointerDown(event: PointerEvent) {
      const target = event.target

      if (target instanceof Node && !alertCenterRef.current?.contains(target)) {
        setIsOpen(false)
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('pointerdown', handlePointerDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('pointerdown', handlePointerDown)
    }
  }, [isOpen])

  const visibleViolations = useMemo(
    () =>
      violations
        .filter((violation) => !violation.dismissed)
        .sort((first, second) => Date.parse(second.lastEventAt) - Date.parse(first.lastEventAt)),
    [violations],
  )

  const alertCount = visibleViolations.length

  function handleSoundToggle() {
    setIsSoundMuted((currentValue) => {
      const nextValue = !currentValue

      writeAlertSoundMuted(nextValue)

      return nextValue
    })
  }

  return (
    <section ref={alertCenterRef} className="alert-center" aria-label="Güvenlik bildirimleri">
      <Button
        type="button"
        variant="secondary"
        className="alert-center__trigger"
        aria-label={`Bildirimleri aç. ${alertCount} aktif bildirim var.`}
        aria-controls="alert-center-panel"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((currentValue) => !currentValue)}
      >
        <Bell size={18} aria-hidden="true" />
        Bildirimler
        {alertCount > 0 && (
          <span className="alert-center__count" aria-live="polite">
            {alertCount}
          </span>
        )}
      </Button>

      {isOpen && (
        <div
          id="alert-center-panel"
          className="alert-center__panel"
          role="region"
          aria-label="Aktif güvenlik bildirimleri"
        >
          <div className="alert-center__header">
            <div>
              <h2>Güvenlik bildirimleri</h2>
              <p>{alertCount} aktif bildirim</p>
            </div>

            <div className="alert-center__controls">
              <button
                type="button"
                className="alert-center__control"
                aria-label={isSoundMuted ? 'Bildirim sesini aç' : 'Bildirim sesini kapat'}
                aria-pressed={isSoundMuted}
                onClick={handleSoundToggle}
              >
                {isSoundMuted ? (
                  <VolumeX size={18} aria-hidden="true" />
                ) : (
                  <Volume2 size={18} aria-hidden="true" />
                )}
              </button>

              <button
                type="button"
                className="alert-center__control"
                aria-label="Bildirim panelini kapat"
                onClick={() => setIsOpen(false)}
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>
          </div>

          {visibleViolations.length === 0 ? (
            <p className="alert-center__empty">Aktif güvenlik bildirimi bulunmuyor.</p>
          ) : (
            <ul className="alert-center__list">
              {visibleViolations.map((violation) => (
                <li key={violation.violationId} className="alert-center__item">
                  <button
                    type="button"
                    className="alert-center__item-open"
                    onClick={() => openViolationDetail(violation.violationId)}
                  >
                    <strong>{getViolationTypeLabel(violation.type)}</strong>

                    <p>
                      {violation.cameraName} · {violation.departmentName}
                    </p>

                    <div className="alert-center__metadata">
                      <span>Güven: {formatConfidence(violation.confidence)}</span>
                      <span>{getRecordingStatusLabel(violation.recordingStatus)}</span>
                      <span>
                        {violation.coverImageReady ? 'Kapak hazır' : 'Kapak hazırlanıyor'}
                      </span>
                    </div>

                    <time dateTime={violation.startedAt}>
                      {formatUtcToLocal(violation.startedAt)}
                    </time>
                  </button>

                  <button
                    type="button"
                    className="alert-center__dismiss"
                    aria-label={`${violation.cameraName} bildirimini kapat`}
                    onClick={(event) => {
                      event.stopPropagation()
                      dismissViolation(violation.violationId)
                    }}
                  >
                    <X size={16} aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

export default AlertCenter
