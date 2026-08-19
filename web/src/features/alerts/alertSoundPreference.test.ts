import { beforeEach, describe, expect, it } from 'vitest'
import { readAlertSoundMuted, writeAlertSoundMuted } from './alertSoundPreference'

describe('alertSoundPreference', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('uses sound by default', () => {
    expect(readAlertSoundMuted()).toBe(false)
  })

  it('persists the muted preference', () => {
    writeAlertSoundMuted(true)

    expect(readAlertSoundMuted()).toBe(true)
  })

  it('persists the unmuted preference', () => {
    writeAlertSoundMuted(true)
    writeAlertSoundMuted(false)

    expect(readAlertSoundMuted()).toBe(false)
  })
})
