import { describe, expect, it } from 'vitest'
import { hasRouteAccess } from './roleAccess'

describe('hasRouteAccess', () => {
  it('allows every role to access public routes', () => {
    expect(hasRouteAccess('public', [])).toBe(true)
  })

  it('allows authenticated roles to access authenticated routes', () => {
    expect(hasRouteAccess('authenticated', ['ADMIN'])).toBe(true)
    expect(hasRouteAccess('authenticated', ['OHS_SPECIALIST'])).toBe(true)
    expect(hasRouteAccess('authenticated', ['SHIFT_SUPERVISOR'])).toBe(true)
  })

  it('allows only ADMIN to access admin routes', () => {
    expect(hasRouteAccess('admin', ['ADMIN'])).toBe(true)
    expect(hasRouteAccess('admin', ['OHS_SPECIALIST'])).toBe(false)
    expect(hasRouteAccess('admin', ['SHIFT_SUPERVISOR'])).toBe(false)
  })

  it('denies authenticated routes when the user has no roles', () => {
    expect(hasRouteAccess('authenticated', [])).toBe(false)
  })
})