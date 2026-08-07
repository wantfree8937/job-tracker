import { useState, type FormEvent } from 'react'
import { login, signup } from '../api'

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [isSignup, setIsSignup] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')

    try {
      if (isSignup) {
        if (password !== passwordConfirm) {
          setError('비밀번호가 일치하지 않습니다')
          return
        }
        await signup(email, password, nickname)
        setIsSignup(false)
        setPassword('')
        setPasswordConfirm('')
        setError('회원가입이 완료되었습니다. 로그인해 주세요.')
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
      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="auth-brand">
          <h1>Job Tracker</h1>
          <p>채용 공고 지원을 한눈에</p>
        </div>
        <h2>{isSignup ? '회원가입' : '로그인'}</h2>
        {error && <p className="error-message">{error}</p>}
        <label>
          이메일
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          비밀번호
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {isSignup && (
          <label>
            비밀번호 확인
            <input
              type="password"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
            />
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
