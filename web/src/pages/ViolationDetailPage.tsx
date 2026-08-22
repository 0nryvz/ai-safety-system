import { useEffect, useMemo, useRef, useState } from 'react'
import { reviewViolation, type EditableViolationReviewStatus } from '../services/violationService'
import ConfirmDialog from '../shared/ui/ConfirmDialog/ConfirmDialog'
import { useParams } from 'react-router-dom'
import AppShell from '../app/AppShell'
import { useViolationDetail } from '../features/violations/useViolationDetail'
import Button from '../shared/ui/Button/Button'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import { formatUtcToLocal } from '../core/date/dateTime'
import {
  getViolationDetailLifecyclePresentation,
  getViolationDetailRecordingPresentation,
  getViolationDetailReviewPresentation,
  getViolationDetailTypeLabel,
} from '../features/violations/violationDetailPresentation'
import StatusBadge from '../shared/ui/StatusBadge/StatusBadge'
import './ViolationDetailPage.css'
import ViolationVideoPlayer from '../features/violations/ViolationVideoPlayer'
import { useRealtimeViolations } from '../core/realtime/useRealtimeViolations'
import { subscribeToRealtimeRecovery } from '../core/realtime/realtimeRuntime'
import { ApiError } from '../core/api/apiError'
import ViolationCoverImage from '../features/violations/ViolationCoverImage'

function ViolationDetailPage() {
  const { id } = useParams<{ id: string }>()

  const { data, isLoading, error, retry } = useViolationDetail(id ?? '')

  const { violations } = useRealtimeViolations()

  const realtimeViolation = useMemo(
    () => violations.find((violation) => violation.violationId === id),
    [id, violations],
  )

  const lastHandledRealtimeEventRef = useRef<string | null>(null)

  const [selectedReviewStatus, setSelectedReviewStatus] =
    useState<EditableViolationReviewStatus>('REVIEWED')
  const [isReviewDialogOpen, setIsReviewDialogOpen] = useState(false)
  const [isReviewSubmitting, setIsReviewSubmitting] = useState(false)
  const [reviewError, setReviewError] = useState<string | null>(null)

  useEffect(() => {
    if (!data || !realtimeViolation) {
      return
    }

    if (lastHandledRealtimeEventRef.current === realtimeViolation.lastEventAt) {
      return
    }

    const detailChanged =
      realtimeViolation.recordingStatus !== data.recordingStatus ||
      realtimeViolation.clipReady !== data.clipReady ||
      realtimeViolation.coverImageReady !== data.coverImageReady ||
      realtimeViolation.lifecycleStatus !== data.lifecycleStatus

    if (!detailChanged) {
      return
    }

    lastHandledRealtimeEventRef.current = realtimeViolation.lastEventAt
    retry()
  }, [data, realtimeViolation, retry])

  useEffect(() => {
    return subscribeToRealtimeRecovery(() => {
      retry()
    })
  }, [retry])

  const lifecyclePresentation = data
    ? getViolationDetailLifecyclePresentation(data.lifecycleStatus)
    : null

  const reviewPresentation = data ? getViolationDetailReviewPresentation(data.reviewStatus) : null

  const recordingPresentation = data
    ? getViolationDetailRecordingPresentation(data.recordingStatus)
    : null

  async function handleReviewConfirm() {
    if (!data || isReviewSubmitting) {
      return
    }

    setIsReviewSubmitting(true)
    setReviewError(null)

    try {
      await reviewViolation(data.violationId, selectedReviewStatus, data.version)
      setIsReviewDialogOpen(false)
      retry()
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status === 409 &&
        error.response?.code === 'VIOLATION_VERSION_CONFLICT'
      ) {
        setReviewError(
          'İhlal başka bir kullanıcı tarafından güncellendi. Güncel bilgileri yükleyip tekrar deneyin.',
        )
        retry()
        return
      }

      setReviewError('İnceleme durumu güncellenemedi. Lütfen tekrar deneyin.')
    } finally {
      setIsReviewSubmitting(false)
    }
  }

  return (
    <AppShell>
      <section>
        {isLoading && !data ? (
          <div role="status" aria-label="İhlal detayı yükleniyor">
            <Skeleton height="32px" width="240px" />
            <Skeleton height="20px" width="60%" />
            <Skeleton height="20px" width="45%" />
          </div>
        ) : error ? (
          <ErrorState
            title="İhlal detayı yüklenemedi"
            description="İhlal bilgileri alınırken bir hata oluştu."
            action={
              <Button type="button" onClick={retry}>
                Tekrar dene
              </Button>
            }
          />
        ) : !data ? (
          <EmptyState title="İhlal bulunamadı" description="İhlal detayı mevcut değil." />
        ) : (
          <div className="violation-detail">
            <header className="violation-detail__header">
              <div>
                <h2>İhlal Detayı</h2>
                <p>{getViolationDetailTypeLabel(data.type)}</p>
              </div>

              {lifecyclePresentation && (
                <StatusBadge variant={lifecyclePresentation.variant}>
                  {lifecyclePresentation.label}
                </StatusBadge>
              )}
            </header>

            <div className="violation-detail__grid">
              <div className="violation-detail__item">
                <span>Kamera</span>
                <strong>
                  {data.cameraName} ({data.cameraCode})
                </strong>
              </div>

              <div className="violation-detail__item">
                <span>Departman</span>
                <strong>{data.departmentName}</strong>
              </div>

              <div className="violation-detail__item">
                <span>Güven oranı</span>
                <strong>%{Math.round(data.confidence * 100)}</strong>
              </div>

              <div className="violation-detail__item">
                <span>Model sürümü</span>
                <strong>{data.modelVersion}</strong>
              </div>

              <div className="violation-detail__item">
                <span>Başlangıç</span>
                <strong>{formatUtcToLocal(data.startedAt)}</strong>
              </div>

              <div className="violation-detail__item">
                <span>Bitiş</span>
                <strong>{data.endedAt ? formatUtcToLocal(data.endedAt) : 'Devam ediyor'}</strong>
              </div>

              <div className="violation-detail__item">
                <span>İnceleme durumu</span>

                {reviewPresentation && (
                  <StatusBadge variant={reviewPresentation.variant}>
                    {reviewPresentation.label}
                  </StatusBadge>
                )}
              </div>

              <div className="violation-detail__item">
                <span>Kayıt durumu</span>

                {recordingPresentation && (
                  <StatusBadge variant={recordingPresentation.variant}>
                    {recordingPresentation.label}
                  </StatusBadge>
                )}
              </div>

              {data.reviewedAt && (
                <div className="violation-detail__item">
                  <span>İnceleme zamanı</span>
                  <strong>{formatUtcToLocal(data.reviewedAt)}</strong>
                </div>
              )}

              {data.reviewedBy && (
                <div className="violation-detail__item">
                  <span>İnceleyen kullanıcı</span>
                  <strong>{data.reviewedBy}</strong>
                </div>
              )}
            </div>
            <section className="violation-detail__media">
              <div>
                <h3>İhlal Kapak Görseli</h3>
                <p>Kapak hazır olduğunda güvenli bağlantı üzerinden gösterilir.</p>
              </div>

              <ViolationCoverImage
                violationId={data.violationId}
                coverImageReady={data.coverImageReady}
              />
            </section>
            <section className="violation-detail__media">
              <div>
                <h3>İhlal Videosu</h3>
                <p>Kayıt hazır olduğunda güvenli bağlantı üzerinden oynatılır.</p>
              </div>

              <ViolationVideoPlayer
                violationId={data.violationId}
                recordingStatus={data.recordingStatus}
              />
            </section>

            <section className="violation-detail__review">
              <div>
                <h3>İhlali İncele</h3>
                <p>Bu ihlal için inceleme sonucunu seçin ve değişikliği onaylayın.</p>
              </div>

              <label htmlFor="violation-review-status">İnceleme sonucu</label>

              <select
                id="violation-review-status"
                value={selectedReviewStatus}
                disabled={isReviewSubmitting}
                onChange={(event) =>
                  setSelectedReviewStatus(event.target.value as EditableViolationReviewStatus)
                }
              >
                <option value="REVIEWED">İncelendi</option>
                <option value="CONFIRMED">Onaylandı</option>
                <option value="FALSE_ALARM">Yanlış alarm</option>
              </select>

              {reviewError && (
                <p className="violation-detail__review-error" role="alert">
                  {reviewError}
                </p>
              )}

              <div>
                <Button
                  type="button"
                  disabled={isReviewSubmitting}
                  onClick={() => setIsReviewDialogOpen(true)}
                >
                  {isReviewSubmitting ? 'Kaydediliyor...' : 'İncelemeyi kaydet'}
                </Button>
              </div>

              <ConfirmDialog
                open={isReviewDialogOpen}
                title="İnceleme durumunu güncelle"
                description="Seçtiğiniz inceleme sonucu kaydedilecek. Devam etmek istiyor musunuz?"
                confirmLabel="Onayla"
                cancelLabel="Vazgeç"
                onConfirm={() => void handleReviewConfirm()}
                onCancel={() => setIsReviewDialogOpen(false)}
              />
            </section>
          </div>
        )}
      </section>
    </AppShell>
  )
}

export default ViolationDetailPage
