import { useState, type FormEvent } from 'react'
import { login, signup } from '../api'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// 비밀번호 보이기/숨기기 아이콘 (20x20, currentColor로 테마 색상 상속)
function EyeIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M1.5 10C1.5 10 4.5 4 10 4C15.5 4 18.5 10 18.5 10C18.5 10 15.5 16 10 16C4.5 16 1.5 10 1.5 10Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="10" cy="10" r="2.5" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M1.5 10C1.5 10 4.5 4 10 4C15.5 4 18.5 10 18.5 10C18.5 10 15.5 16 10 16C4.5 16 1.5 10 1.5 10Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="10" cy="10" r="2.5" stroke="currentColor" strokeWidth="1.5" />
      <line x1="3" y1="17" x2="17" y2="3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [isSignup, setIsSignup] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess('')

    if (!EMAIL_REGEX.test(email)) {
      setError('올바른 이메일 형식이 아닙니다')
      return
    }

    try {
      if (isSignup) {
        if (password !== passwordConfirm) {
          setError('비밀번호가 일치하지 않습니다')
          return
        }
        await signup(email, password, nickname)
        // 가입 직후 바로 로그인시켜 재입력 없이 메인 화면으로 진입시킨다
        await login(email, password)
        setSuccess('회원가입이 완료되었습니다.')
        onLogin()
      } else {
        await login(email, password)
        onLogin()
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '오류가 발생했습니다.')
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-brand">
          <h1>Job Tracker</h1>
          <p>채용 공고 지원을 한눈에</p>
        </div>
        <h2>{isSignup ? '회원가입' : '로그인'}</h2>
        <div className="auth-message">
          {error && <p className="error-message">{error}</p>}
          {success && <p className="success-message">{success}</p>}
        </div>
        <label>
          이메일
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          비밀번호
          <div className="password-input-wrapper">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="password-toggle-button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
              title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
            >
              {showPassword ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          </div>
        </label>
        {isSignup && (
          <label>
            비밀번호 확인
            <div className="password-input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
              />
              <button
                type="button"
                className="password-toggle-button"
                onClick={() => setShowPasswordConfirm((v) => !v)}
                aria-label={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
                title={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
              >
                {showPasswordConfirm ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
          </label>
        )}
        {isSignup && (
          <label>
            닉네임
            <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)} />
          </label>
        )}
        <button type="submit" className="primary-button">
          {isSignup ? '가입하기' : '로그인'}
        </button>
        <button type="button" className="link-button" onClick={() => setIsSignup(!isSignup)}>
          {isSignup ? '로그인으로 돌아가기' : '계정이 없나요? 회원가입'}
        </button>
      </form>
    </div>
  )
}
