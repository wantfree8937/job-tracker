import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import JobFormModal from './JobFormModal'
import { previewLink } from '../api'

vi.mock('../api', () => ({
  createJob: vi.fn(),
  updateJob: vi.fn(),
  previewLink: vi.fn(),
}))

describe('JobFormModal', () => {
  beforeEach(() => {
    vi.mocked(previewLink).mockReset()
  })

  it('회사명과 포지션 없이 제출하면 검증 메시지를 보여준다', async () => {
    const user = userEvent.setup()
    render(<JobFormModal job={null} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('회사명과 포지션은 필수입니다.')).toBeInTheDocument()
  })

  it('자동 채우기 버튼을 클릭하면 링크 프리뷰로 빈 필드를 채운다', async () => {
    vi.mocked(previewLink).mockResolvedValue({ company: '카카오', position: '백엔드 개발자', memo: null })
    const user = userEvent.setup()
    render(<JobFormModal job={null} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.type(screen.getByLabelText('링크'), 'https://example.com/job')
    await user.click(screen.getByRole('button', { name: '자동 채우기' }))

    expect(previewLink).toHaveBeenCalledWith('https://example.com/job')
    expect(await screen.findByDisplayValue('카카오')).toBeInTheDocument()
    expect(screen.getByDisplayValue('백엔드 개발자')).toBeInTheDocument()
  })

  it('링크 프리뷰 실패 시 에러 메시지를 보여준다', async () => {
    vi.mocked(previewLink).mockRejectedValue(new Error('링크 정보를 가져올 수 없습니다'))
    const user = userEvent.setup()
    render(<JobFormModal job={null} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.type(screen.getByLabelText('링크'), 'https://example.com/job')
    await user.click(screen.getByRole('button', { name: '자동 채우기' }))

    expect(await screen.findByText('링크 정보를 가져올 수 없습니다')).toBeInTheDocument()
  })
})
