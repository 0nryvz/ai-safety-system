import { useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import type { RestrictedZonePoint } from '../../services/cameraService'
import { clampPoint } from './polygonUtils'
import './RestrictedZoneEditor.css'

interface RestrictedZoneEditorProps {
  points: RestrictedZonePoint[]
  onChange: (points: RestrictedZonePoint[]) => void
  imageUrl?: string | null
}

function RestrictedZoneEditor({ points, onChange, imageUrl }: RestrictedZoneEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [draggingPointIndex, setDraggingPointIndex] = useState<number | null>(null)

  function getNormalizedPoint(clientX: number, clientY: number): RestrictedZonePoint | null {
    const container = containerRef.current

    if (!container) {
      return null
    }

    const rect = container.getBoundingClientRect()

    if (rect.width === 0 || rect.height === 0) {
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
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
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
