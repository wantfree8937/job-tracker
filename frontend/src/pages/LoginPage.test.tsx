import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
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

  it('빈 입력으로 제출하면 빈 값 그대로 로그인 API가 호출된다', async () => {
    vi.mocked(login).mockResolvedValue({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600 })
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(login).toHaveBeenCalledWith('', '')
  })

  it('회원가입 성공 시 로그인 화면으로 전환되고 안내 메시지를 보여준다', async () => {
    vi.mocked(signup).mockResolvedValue({ id: 1, email: 'test@example.com', nickname: 'tester', createdAt: '2026-08-07', keywords: [] })
    const user = userEvent.setup()
    render(<LoginPage onLogin={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '계정이 없나요? 회원가입' }))
    await user.type(screen.getByLabelText('이메일'), 'test@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('닉네임'), 'tester')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByText('회원가입이 완료되었습니다. 로그인해 주세요.')).toBeInTheDocument()
  })
})
