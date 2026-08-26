import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import type { RestrictedZonePoint } from '../../services/cameraService'
import { clampPoint, getImageContentRect } from './polygonUtils'
import './RestrictedZoneEditor.css'

interface RestrictedZoneEditorProps {
  points: RestrictedZonePoint[]
  onChange: (points: RestrictedZonePoint[]) => void
  imageUrl?: string | null
}

function RestrictedZoneEditor({ points, onChange, imageUrl }: RestrictedZoneEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const imageRef = useRef<HTMLImageElement>(null)
  const [draggingPointIndex, setDraggingPointIndex] = useState<number | null>(null)
  const [overlayStyle, setOverlayStyle] = useState({
    left: 0,
    top: 0,
    width: 0,
    height: 0,
  })

  function updateOverlayStyle() {
    const container = containerRef.current
    const image = imageRef.current

    if (!container || !image) {
      return
    }

    const containerRect = container.getBoundingClientRect()
    const contentRect = getImageContentRect(
      image.getBoundingClientRect(),
      image.naturalWidth,
      image.naturalHeight,
    )

    if (!contentRect) {
      return
    }

    setOverlayStyle({
      left: contentRect.left - containerRect.left,
      top: contentRect.top - containerRect.top,
      width: contentRect.width,
      height: contentRect.height,
    })
  }

  useEffect(() => {
    if (!imageUrl) {
      return
    }

    updateOverlayStyle()

    window.addEventListener('resize', updateOverlayStyle)

    return () => {
      window.removeEventListener('resize', updateOverlayStyle)
    }
  }, [imageUrl])

  function getNormalizedPoint(clientX: number, clientY: number): RestrictedZonePoint | null {
    const image = imageRef.current
    const container = containerRef.current

    if (!container) {
      return null
    }

    const rect = image
      ? getImageContentRect(image.getBoundingClientRect(), image.naturalWidth, image.naturalHeight)
      : container.getBoundingClientRect()

    if (!rect || rect.width <= 0 || rect.height <= 0) {
      return null
    }

    if (
      clientX < rect.left ||
      clientX > rect.right ||
      clientY < rect.top ||
      clientY > rect.bottom
    ) {
      return null
    }

    return clampPoint({
      x: (clientX - rect.left) / rect.width,
      y: (clientY - rect.top) / rect.height,
    })
  }

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    if (draggingPointIndex !== null) {
      return
    }

    const point = getNormalizedPoint(event.clientX, event.clientY)

    if (!point) {
      return
    }

    onChange([...points, point])
  }

  function handleMouseMove(event: MouseEvent<HTMLDivElement>) {
    if (draggingPointIndex === null) {
      return
    }

    const point = getNormalizedPoint(event.clientX, event.clientY)

    if (!point) {
      return
    }

    const nextPoints = points.map((currentPoint, index) =>
      index === draggingPointIndex ? point : currentPoint,
    )

    onChange(nextPoints)
  }

  function handleMouseUp() {
    setDraggingPointIndex(null)
  }

  function handleMouseLeave() {
    setDraggingPointIndex(null)
  }

  const polygonPoints = points.map((point) => `${point.x * 100},${point.y * 100}`).join(' ')

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label="Yasaklı alan çizim alanı"
      onClick={handleClick}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseLeave}
      className="restricted-zone-editor"
    >
      {imageUrl ? (
        <img
          src={imageUrl}
          ref={imageRef}
          onLoad={updateOverlayStyle}
          alt=""
          aria-label="Kamera referans görüntüsü"
          draggable={false}
          className="restricted-zone-editor__reference-image"
        />
      ) : (
        <div
          aria-label="Referans görüntü bekleniyor"
          className="restricted-zone-editor__placeholder"
        >
          Referans görüntü bekleniyor
        </div>
      )}

      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        aria-hidden="true"
        data-testid="restricted-zone-overlay"
        style={{
          position: 'absolute',
          left: `${overlayStyle.left}px`,
          top: `${overlayStyle.top}px`,
          width: `${overlayStyle.width}px`,
          height: `${overlayStyle.height}px`,
        }}
      >
        {points.length >= 2 && (
          <polyline
            points={polygonPoints}
            fill="none"
            stroke="currentColor"
            strokeWidth="1"
            vectorEffect="non-scaling-stroke"
          />
        )}

        {points.length >= 3 && (
          <polygon
            points={polygonPoints}
            fill="currentColor"
            fillOpacity="0.12"
            stroke="currentColor"
            strokeWidth="1"
            vectorEffect="non-scaling-stroke"
          />
        )}

        {points.map((point, index) => (
          <circle
            key={`${point.x}-${point.y}-${index}`}
            cx={point.x * 100}
            cy={point.y * 100}
            r="1.5"
            fill="currentColor"
            data-testid={`restricted-zone-point-${index}`}
            onMouseDown={(event) => {
              event.stopPropagation()
              setDraggingPointIndex(index)
            }}
          />
        ))}
      </svg>
    </div>
  )
}

export default RestrictedZoneEditor
