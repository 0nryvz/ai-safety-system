import type { RestrictedZonePoint } from '../../services/cameraService'

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
