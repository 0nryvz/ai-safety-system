import type { RealtimeRecordingStatus } from '../../core/realtime/realtimeTypes'
import Button from '../../shared/ui/Button/Button'
import EmptyState from '../../shared/ui/EmptyState/EmptyState'
import ErrorState from '../../shared/ui/ErrorState/ErrorState'
import { useViolationClipUrl } from './useViolationClipUrl'
import { getVideoPlayerErrorMessage } from './videoPlayerError'
import { getVideoPlayerViewState } from './videoPlayerState'
import { useState } from 'react'
import './ViolationVideoPlayer.css'

export interface ViolationVideoPlayerProps {
  violationId: string
  recordingStatus: RealtimeRecordingStatus
}

function ViolationVideoPlayer({ violationId, recordingStatus }: ViolationVideoPlayerProps) {
  const viewState = getVideoPlayerViewState(recordingStatus)
  const { data, isLoading, error, retry } = useViolationClipUrl(violationId, recordingStatus)

  const [playbackRetryAttempted, setPlaybackRetryAttempted] = useState(false)
  const [playbackError, setPlaybackError] = useState(false)

  function handlePlaybackError() {
    if (!playbackRetryAttempted) {
      setPlaybackRetryAttempted(true)
      retry()
      return
    }

    setPlaybackError(true)
  }

  function handleManualPlaybackRetry() {
    setPlaybackError(false)
    setPlaybackRetryAttempted(false)
    retry()
  }

  if (viewState.kind === 'preparing') {
    return <EmptyState title={viewState.title} description={viewState.description} />
  }

  if (viewState.kind === 'error') {
    return <ErrorState title={viewState.title} description={viewState.description} />
  }

  if (isLoading) {
    return (
      <EmptyState title="Video yükleniyor" description="Güvenli video bağlantısı hazırlanıyor." />
    )
  }

  if (error) {
    const errorMessage = getVideoPlayerErrorMessage(error)

    return (
      <ErrorState
        title={errorMessage.title}
        description={errorMessage.description}
        action={
          errorMessage.canRetry ? (
            <Button type="button" variant="secondary" onClick={retry}>
              Tekrar dene
            </Button>
          ) : undefined
        }
      />
    )
  }

  if (playbackError) {
    return (
      <ErrorState
        title="Video oynatılamıyor"
        description="Video biçimi tarayıcı tarafından desteklenmiyor veya güvenli bağlantının süresi dolmuş olabilir."
        action={
          <Button type="button" variant="secondary" onClick={handleManualPlaybackRetry}>
            Tekrar dene
          </Button>
        }
      />
    )
  }

  if (!data) {
    return (
      <EmptyState
        title="Video bağlantısı bekleniyor"
        description="Güvenli video bağlantısı henüz alınmadı."
      />
    )
  }

  return (
    <section className="violation-video-player" aria-label="İhlal videosu">
      <video
        className="violation-video-player__media"
        aria-label="İhlal videosu oynatıcı"
        controls
        preload="metadata"
        src={data.url}
        onError={handlePlaybackError}
      >
        Tarayıcınız video oynatmayı desteklemiyor.
      </video>
    </section>
  )
}

export default ViolationVideoPlayer
