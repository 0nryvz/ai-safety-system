const ALERT_SOUND_MUTED_STORAGE_KEY = 'ai-safety.alert-sound-muted'

export function readAlertSoundMuted(): boolean {
  try {
    return window.localStorage.getItem(ALERT_SOUND_MUTED_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

export function writeAlertSoundMuted(isMuted: boolean): void {
  try {
    window.localStorage.setItem(ALERT_SOUND_MUTED_STORAGE_KEY, String(isMuted))
  } catch {
    // Depolama kullanılamasa da geçerli oturumdaki tercih çalışmaya devam eder.
  }
}
