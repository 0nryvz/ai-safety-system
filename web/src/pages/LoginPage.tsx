import { useState, type FormEvent } from 'react'
import { Eye, EyeOff, ShieldCheck } from 'lucide-react'
import { AuthError, login } from '../services/authService'
import './LoginPage.css'

interface LoginPageProps {
  onLoginSuccess: () => void
}

function LoginPage({ onLoginSuccess }: LoginPageProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')

    if (!email.trim() || !password.trim()) {
      setError('Lütfen e-posta ve parola alanlarını doldurun.')
      return
    }

    if (!email.includes('@')) {
      setError('Lütfen geçerli bir e-posta adresi girin.')
      return
    }

    setIsLoading(true)

    try {
      const response = await login({
        email: email.trim(),
        password,
      })

      sessionStorage.setItem('accessToken', response.accessToken)
      onLoginSuccess()
    } catch (caughtError) {
      if (caughtError instanceof AuthError) {
        if (caughtError.status === 401) {
          setError('E-posta adresi veya parola hatalı.')
        } else if (caughtError.status === 403) {
          setError('Hesabınız pasif veya erişime kapalı.')
        } else if (caughtError.status >= 500) {
          setError('Sunucu hatası oluştu. Lütfen daha sonra tekrar deneyin.')
        } else {
          setError('Giriş işlemi başarısız oldu.')
        }
      } else {
        setError(
          'Sunucuya bağlanılamadı. İnternet bağlantınızı veya backend servisini kontrol edin.',
        )
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <div className="login-logo">
            <ShieldCheck size={30} aria-hidden="true" />
          </div>

          <h1>AI Safety System</h1>
          <p>Yönetim paneline giriş yapın</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label htmlFor="email">E-posta adresi</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="ornek@firma.com"
              autoComplete="email"
              disabled={isLoading}
            />
          </div>

          <div className="form-field">
            <label htmlFor="password">Parola</label>

            <div className="password-field">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Parolanızı girin"
                autoComplete="current-password"
                disabled={isLoading}
              />

              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((current) => !current)}
                aria-label={showPassword ? 'Parolayı gizle' : 'Parolayı göster'}
                disabled={isLoading}
              >
                {showPassword ? (
                  <EyeOff size={20} aria-hidden="true" />
                ) : (
                  <Eye size={20} aria-hidden="true" />
                )}
              </button>
            </div>
          </div>

          {error && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}

          <button className="login-button" type="submit" disabled={isLoading}>
            {isLoading ? 'Giriş yapılıyor...' : 'Giriş yap'}
          </button>
        </form>
      </section>
    </main>
  )
}

export default LoginPage
