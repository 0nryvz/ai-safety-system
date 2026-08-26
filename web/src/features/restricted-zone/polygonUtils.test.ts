import { describe, expect, it } from 'vitest'
import {
  clampNormalizedValue,
  clampPoint,
  getImageContentRect,
  hasMinimumPolygonPoints,
  hasSelfIntersection,
} from './polygonUtils'

const BOX_16_9 = {
  left: 0,
  top: 0,
  width: 1600,
  height: 900,
}

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

  it('maps a 16:9 image onto a 16:9 box without letterbox', () => {
    expect(getImageContentRect(BOX_16_9, 1600, 900)).toEqual({
      left: 0,
      top: 0,
      width: 1600,
      height: 900,
      right: 1600,
      bottom: 900,
    })
  })

  it('maps a 4:3 image with left and right letterbox', () => {
    expect(getImageContentRect(BOX_16_9, 1200, 900)).toEqual({
      left: 200,
      top: 0,
      width: 1200,
      height: 900,
      right: 1400,
      bottom: 900,
    })
  })

  it('maps a portrait image with left and right letterbox', () => {
    expect(getImageContentRect(BOX_16_9, 900, 1600)).toEqual({
      left: 546.875,
      top: 0,
      width: 506.25,
      height: 900,
      right: 1053.125,
      bottom: 900,
    })
  })

  it('maps a wide image with top and bottom letterbox', () => {
    const contentRect = getImageContentRect(BOX_16_9, 1920, 800)

    expect(contentRect).not.toBeNull()
    expect(contentRect?.left).toBe(0)
    expect(contentRect?.width).toBe(1600)
    expect(contentRect?.height).toBeCloseTo(1600 / (1920 / 800))
    expect(contentRect?.top).toBeCloseTo((900 - 1600 / (1920 / 800)) / 2)
    expect(contentRect?.right).toBe(1600)
    expect(contentRect?.bottom).toBeCloseTo(contentRect!.top + contentRect!.height)
  })

  it('returns null for invalid natural size or element box', () => {
    expect(getImageContentRect(BOX_16_9, 0, 900)).toBeNull()
    expect(getImageContentRect(BOX_16_9, 1200, 0)).toBeNull()
    expect(getImageContentRect({ ...BOX_16_9, width: 0 }, 1200, 900)).toBeNull()
    expect(getImageContentRect({ ...BOX_16_9, height: 0 }, 1200, 900)).toBeNull()
    expect(getImageContentRect(BOX_16_9, Number.NaN, 900)).toBeNull()
    expect(getImageContentRect(BOX_16_9, 1200, Number.POSITIVE_INFINITY)).toBeNull()
  })
})
