import {
  browserLocalPersistence,
  browserSessionPersistence,
  setPersistence,
  signInWithEmailAndPassword,
} from 'firebase/auth'
import { useState, type FormEvent } from 'react'
import { LockIcon, MailIcon } from '../components/icons'
import { auth } from '../firebase'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await setPersistence(auth, remember ? browserLocalPersistence : browserSessionPersistence)
      await signInWithEmailAndPassword(auth, email, password)
    } catch {
      setError('Giriş başarısız. E-posta veya şifreyi kontrol et.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-visual">
        <div className="auth-visual-body">
          <div className="auth-badge" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="4" y="2" width="16" height="20" rx="2" />
              <path d="M11 18h2" />
            </svg>
          </div>
          <h2>Nokta Yemek Kiosk Yönetim Paneli</h2>
          <p>
            Sahadaki tabletlerin kilit durumunu, pil seviyesini ve bağlantısını tek bir
            noktadan izle; gerektiğinde anında müdahale et.
          </p>
        </div>
        <div className="auth-visual-foot">© {new Date().getFullYear()} Nokta Yemek</div>
      </div>

      <div className="auth-form-side">
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-form-head">
            <img src="/nylogo.png" alt="Nokta Yemek" className="auth-form-logo-img" />
            <h1>Tekrar Hoş Geldiniz</h1>
            <p className="subtitle">Kiosk paneline giriş yapın</p>
          </div>

          <div className="field">
            <label htmlFor="email">E-posta</label>
            <div className="field-input">
              <span className="icon" aria-hidden="true">
                <MailIcon size={17} />
              </span>
              <input
                id="email"
                type="email"
                placeholder="mail@adresiniz.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
          </div>

          <div className="field">
            <label htmlFor="password">Şifre</label>
            <div className="field-input">
              <span className="icon" aria-hidden="true">
                <LockIcon size={17} />
              </span>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
              <button type="button" onClick={() => setShowPassword((v) => !v)} aria-label="Şifreyi göster/gizle">
                {showPassword ? 'Gizle' : 'Göster'}
              </button>
            </div>
          </div>

          <div className="remember-row">
            <label>
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              Beni Hatırla
            </label>
          </div>

          {error && <p className="error-text">{error}</p>}

          <button type="submit" className="btn-navy" disabled={submitting}>
            {submitting ? 'Giriş yapılıyor…' : 'Giriş Yap →'}
          </button>
        </form>
      </div>
    </div>
  )
}
