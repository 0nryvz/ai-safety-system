import { describe, expect, it } from 'vitest'
import {
  clampNormalizedValue,
  clampPoint,
  hasMinimumPolygonPoints,
  hasSelfIntersection,
} from './polygonUtils'

describe('polygonUtils', () => {
  it('clamps normalized values between zero and one', () => {
    expect(clampNormalizedValue(-0.2)).toBe(0)
    expect(clampNormalizedValue(0.5)).toBe(0.5)
    expect(clampNormalizedValue(1.4)).toBe(1)
  })

  it('clamps point coordinates', () => {
    expect(
      clampPoint({
        x: -0.1,
        y: 1.2,
      }),
    ).toEqual({
      x: 0,
      y: 1,
    })
  })

  it('requires at least three polygon points', () => {
    expect(
      hasMinimumPolygonPoints([
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.1 },
      ]),
    ).toBe(false)

    expect(
      hasMinimumPolygonPoints([
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.1 },
        { x: 0.9, y: 0.9 },
      ]),
    ).toBe(true)
  })

  it('detects a self-intersecting polygon', () => {
    expect(
      hasSelfIntersection([
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.9 },
        { x: 0.1, y: 0.9 },
        { x: 0.9, y: 0.1 },
      ]),
    ).toBe(true)
  })

  it('accepts a simple polygon', () => {
    expect(
      hasSelfIntersection([
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.1 },
        { x: 0.9, y: 0.9 },
        { x: 0.1, y: 0.9 },
      ]),
    ).toBe(false)
  })
})
