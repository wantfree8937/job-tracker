import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import JobFormModal from './JobFormModal'

describe('JobFormModal', () => {
  it('회사명과 포지션 없이 제출하면 검증 메시지를 보여준다', async () => {
    const user = userEvent.setup()
    render(<JobFormModal job={null} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('회사명과 포지션은 필수입니다.')).toBeInTheDocument()
  })
})
