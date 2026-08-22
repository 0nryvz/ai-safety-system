import { useState } from 'react'
import Button from '../../shared/ui/Button/Button'
import EmptyState from '../../shared/ui/EmptyState/EmptyState'
import ErrorState from '../../shared/ui/ErrorState/ErrorState'
import { useViolationCoverUrl } from './useViolationCoverUrl'

export interface ViolationCoverImageProps {
  violationId: string
  coverImageReady: boolean
}

function ViolationCoverImage({ violationId, coverImageReady }: ViolationCoverImageProps) {
  const { data, isLoading, error, retry } = useViolationCoverUrl(violationId, coverImageReady)

  const [imageError, setImageError] = useState(false)

  function handleManualRetry() {
    setImageError(false)
    retry()
  }

  if (!coverImageReady) {
    return (
      <EmptyState title="Kapak hazırlanıyor" description="İhlal kapak görseli henüz hazır değil." />
    )
  }

  if (isLoading) {
    return (
      <EmptyState
        title="Kapak yükleniyor"
        description="Güvenli kapak görseli bağlantısı hazırlanıyor."
      />
    )
  }

  if (error) {
    return (
      <ErrorState
        title="Kapak görseli yüklenemedi"
        description="Güvenli kapak görseli bağlantısı alınamadı."
        action={
          <Button type="button" variant="secondary" onClick={retry}>
            Tekrar dene
          </Button>
        }
      />
    )
  }

  if (imageError) {
    return (
      <ErrorState
        title="Kapak görseli gösterilemiyor"
        description="Görsel bağlantısının süresi dolmuş veya görsel yüklenememiş olabilir."
        action={
          <Button type="button" variant="secondary" onClick={handleManualRetry}>
            Tekrar dene
          </Button>
        }
      />
    )
  }

  if (!data) {
    return (
      <EmptyState
        title="Kapak bağlantısı bekleniyor"
        description="Güvenli kapak görseli bağlantısı henüz alınmadı."
      />
    )
  }

  return (
    <img
      src={data.url}
      alt="İhlal kapak görseli"
      loading="lazy"
      onError={() => setImageError(true)}
    />
  )
}

export default ViolationCoverImage
