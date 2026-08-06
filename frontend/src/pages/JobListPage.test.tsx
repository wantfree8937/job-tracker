import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import JobListPage from './JobListPage'
import type { JobPosting } from '../types'

const jobA: JobPosting = {
  id: 1,
  companyName: '카카오',
  position: '백엔드 개발자',
  status: 'WISH',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const jobB: JobPosting = {
  id: 2,
  companyName: '네이버',
  position: '프론트엔드 개발자',
  status: 'APPLIED',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('JobListPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('상태 필터 칩을 클릭하면 해당 상태의 공고만 조회한다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ WISH: 1, APPLIED: 1 }) })
        }
        if (url.includes('status=APPLIED')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => [jobB] })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [jobA, jobB] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    // 초기 로딩: 전체 공고 표시
    await waitFor(() => expect(screen.getByText('카카오')).toBeInTheDocument())
    expect(screen.getByText('네이버')).toBeInTheDocument()

    // "지원함" 필터 칩 클릭 → 네이버만 남는다
    await user.click(screen.getByRole('button', { name: '지원함' }))

    await waitFor(() => expect(screen.queryByText('카카오')).not.toBeInTheDocument())
    expect(screen.getByText('네이버')).toBeInTheDocument()
  })
})
