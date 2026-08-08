import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoginPage from './LoginPage'
import { login, signup } from '../api'

vi.mock('../api', () => ({
  login: vi.fn(),
  signup: vi.fn(),
}))

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(login).mockReset()
    vi.mocked(signup).mockReset()
  })

  it('기본적으로 로그인 폼을 렌더링한다', () => {
    render(<LoginPage onLogin={vi.fn()} />)

    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })

  it('회원가입 링크를 클릭하면 회원가입 폼으로 전환된다', async () => {
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '계정이 없나요? 회원가입' }))

    expect(screen.getByRole('heading', { name: '회원가입' })).toBeInTheDocument()
    expect(screen.getByText('닉네임')).toBeInTheDocument()
  })

  it('로그인 성공 시 onLogin 콜백을 호출한다', async () => {
    vi.mocked(login).mockResolvedValue({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600 })
    const onLogin = vi.fn()
    const user = userEvent.setup()
    render(<LoginPage onLogin={onLogin} />)

    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(login).toHaveBeenCalledWith('test@example.com', 'password123')
    expect(onLogin).toHaveBeenCalled()
  })

  it('로그인 실패 시 에러 메시지를 화면에 표시한다', async () => {
    vi.mocked(login).mockRejectedValue(new Error('이메일 또는 비밀번호가 올바르지 않습니다.'))
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다.')).toBeInTheDocument()
  })

  it('빈 입력으로 제출하면 이메일 형식 오류를 표시하고 로그인 API를 호출하지 않는다', async () => {
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('올바른 이메일 형식이 아닙니다')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('이메일 형식이 올바르지 않으면 에러 메시지를 표시하고 API를 호출하지 않는다', async () => {
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.type(screen.getByLabelText('이메일'), 'invalid-email')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('올바른 이메일 형식이 아닙니다')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('회원가입 성공 시 자동으로 로그인되어 onLogin이 호출된다', async () => {
    vi.mocked(signup).mockResolvedValue({ id: 1, email: 'test@example.com', nickname: 'tester', createdAt: '2026-08-07', keywords: [] })
    vi.mocked(login).mockResolvedValue({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600 })
    const onLogin = vi.fn()
    const user = userEvent.setup()
    render(<LoginPage onLogin={onLogin} />)

    await user.click(screen.getByRole('button', { name: '계정이 없나요? 회원가입' }))
    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
    await user.type(screen.getByLabelText('닉네임'), 'tester')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(signup).toHaveBeenCalledWith('test@example.com', 'password123', 'tester')
    await waitFor(() => expect(login).toHaveBeenCalledWith('test@example.com', 'password123'))
    await waitFor(() => expect(onLogin).toHaveBeenCalled())
  })

  it('회원가입 성공 메시지는 success-message 클래스로 렌더링된다', async () => {
    vi.mocked(signup).mockResolvedValue({ id: 1, email: 'test@example.com', nickname: 'tester', createdAt: '2026-08-07', keywords: [] })
    vi.mocked(login).mockResolvedValue({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600 })
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '계정이 없나요? 회원가입' }))
    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123')
    await user.type(screen.getByLabelText('닉네임'), 'tester')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    const message = await screen.findByText('회원가입이 완료되었습니다.')
    expect(message).toHaveClass('success-message')
  })

  it('비밀번호 보기 버튼을 클릭하면 input type이 text로 바뀐다', async () => {
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    const passwordInput = screen.getByLabelText('비밀번호') as HTMLInputElement
    expect(passwordInput.type).toBe('password')

    await user.click(screen.getByRole('button', { name: '비밀번호 보기' }))

    expect(passwordInput.type).toBe('text')
  })

  it('비밀번호와 비밀번호 확인이 다르면 에러 메시지를 표시하고 가입 API를 호출하지 않는다', async () => {
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '계정이 없나요? 회원가입' }))
    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password456')
    await user.type(screen.getByLabelText('닉네임'), 'tester')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('비밀번호가 일치하지 않습니다')).toBeInTheDocument()
    expect(signup).not.toHaveBeenCalled()
  })
})
