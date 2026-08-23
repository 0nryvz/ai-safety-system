import type { RestrictedZonePoint } from '../../services/cameraService'

export type ImageContentRect = {
  left: number
  top: number
  width: number
  height: number
  right: number
  bottom: number
}

function isPositiveFinite(value: number): boolean {
  return Number.isFinite(value) && value > 0
}

export function getImageContentRect(
  elementRect: Pick<DOMRect, 'left' | 'top' | 'width' | 'height'>,
  naturalWidth: number,
  naturalHeight: number,
): ImageContentRect | null {
  if (
    !Number.isFinite(elementRect.left) ||
    !Number.isFinite(elementRect.top) ||
    !isPositiveFinite(elementRect.width) ||
    !isPositiveFinite(elementRect.height) ||
    !isPositiveFinite(naturalWidth) ||
    !isPositiveFinite(naturalHeight)
  ) {
    return null
  }

  const naturalAspect = naturalWidth / naturalHeight
  const boxAspect = elementRect.width / elementRect.height

  let renderedWidth: number
  let renderedHeight: number
  let offsetX: number
  let offsetY: number

  if (naturalAspect > boxAspect) {
    renderedWidth = elementRect.width
    renderedHeight = renderedWidth / naturalAspect
    offsetX = 0
    offsetY = (elementRect.height - renderedHeight) / 2
  } else {
    renderedHeight = elementRect.height
    renderedWidth = renderedHeight * naturalAspect
    offsetY = 0
    offsetX = (elementRect.width - renderedWidth) / 2
  }

  const left = elementRect.left + offsetX
  const top = elementRect.top + offsetY

  return {
    left,
    top,
    width: renderedWidth,
    height: renderedHeight,
    right: left + renderedWidth,
    bottom: top + renderedHeight,
  }
}

export function clampNormalizedValue(value: number): number {
  return Math.min(1, Math.max(0, value))
}

export function clampPoint(point: RestrictedZonePoint): RestrictedZonePoint {
  return {
    x: clampNormalizedValue(point.x),
    y: clampNormalizedValue(point.y),
  }
}

export function hasMinimumPolygonPoints(points: RestrictedZonePoint[]): boolean {
  return points.length >= 3
}

function orientation(
  a: RestrictedZonePoint,
  b: RestrictedZonePoint,
  c: RestrictedZonePoint,
): number {
  return (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
}

function segmentsIntersect(
  a1: RestrictedZonePoint,
  a2: RestrictedZonePoint,
  b1: RestrictedZonePoint,
  b2: RestrictedZonePoint,
): boolean {
  const o1 = orientation(a1, a2, b1)
  const o2 = orientation(a1, a2, b2)
  const o3 = orientation(b1, b2, a1)
  const o4 = orientation(b1, b2, a2)

  return o1 > 0 !== o2 > 0 && o3 > 0 !== o4 > 0
}

export function hasSelfIntersection(points: RestrictedZonePoint[]): boolean {
  if (points.length < 4) {
    return false
  }

  for (let i = 0; i < points.length; i += 1) {
    const a1 = points[i]
    const a2 = points[(i + 1) % points.length]

    for (let j = i + 1; j < points.length; j += 1) {
      const b1 = points[j]
      const b2 = points[(j + 1) % points.length]

      const sharesVertex = i === j || (i + 1) % points.length === j || i === (j + 1) % points.length

      if (sharesVertex) {
        continue
      }

      if (segmentsIntersect(a1, a2, b1, b2)) {
        return true
      }
    }
  }

  return false
}
