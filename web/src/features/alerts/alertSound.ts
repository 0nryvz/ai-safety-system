export function playAlertSound(): void {
  const AudioContextConstructor = window.AudioContext

  if (!AudioContextConstructor) {
    return
  }

  try {
    const audioContext = new AudioContextConstructor()
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    const startTime = audioContext.currentTime

    oscillator.type = 'sine'
    oscillator.frequency.setValueAtTime(880, startTime)
    oscillator.frequency.exponentialRampToValueAtTime(660, startTime + 0.18)

    gain.gain.setValueAtTime(0.0001, startTime)
    gain.gain.exponentialRampToValueAtTime(0.18, startTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, startTime + 0.22)

    oscillator.connect(gain)
    gain.connect(audioContext.destination)

    oscillator.addEventListener('ended', () => {
      void audioContext.close()
    })

    oscillator.start(startTime)
    oscillator.stop(startTime + 0.22)
  } catch {
    // Ses API'si kullanılamıyorsa görsel bildirim çalışmaya devam eder.
  }
}
