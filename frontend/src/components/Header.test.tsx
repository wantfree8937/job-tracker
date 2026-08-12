import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Header from './Header'

describe('Header', () => {
  it('각 버튼 클릭 시 해당 콜백을 호출한다', async () => {
    const user = userEvent.setup()
    const onLogout = vi.fn()
    const onOpenKeywords = vi.fn()
    const onOpenInterview = vi.fn()
    const onOpenProfile = vi.fn()

    render(
      <Header
        onLogout={onLogout}
        onOpenKeywords={onOpenKeywords}
        onOpenInterview={onOpenInterview}
        onOpenProfile={onOpenProfile}
      />,
    )

    await user.click(screen.getByRole('button', { name: '관심 분야' }))
    expect(onOpenKeywords).toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'AI 면접 질문' }))
    expect(onOpenInterview).toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '내 이력서' }))
    expect(onOpenProfile).toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(onLogout).toHaveBeenCalled()
  })

  it('로그아웃 클릭 시 accessToken을 제거한다', async () => {
    localStorage.setItem('accessToken', 'token123')
    const user = userEvent.setup()

    render(
      <Header onLogout={vi.fn()} onOpenKeywords={vi.fn()} onOpenInterview={vi.fn()} onOpenProfile={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})
