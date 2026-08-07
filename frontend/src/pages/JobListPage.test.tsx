import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
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

  it('전체 공고 탭으로 전환하면 수집 공고 목록을 조회해 보여준다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        if (url.includes('/jobs/collected')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => [
              {
                id: 1,
                company: '토스',
                title: '백엔드 개발자',
                url: 'https://www.jobkorea.co.kr/Recruit/GI_Read/1',
                source: '잡코리아',
                jobKey: '잡코리아:1',
                createdAt: '2026-01-01T00:00:00Z',
              },
            ],
          })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    // 초기 화면은 "내 공고" 탭이므로 수집 공고는 아직 없다
    expect(screen.queryByText('토스')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '전체 공고' }))

    await waitFor(() => expect(screen.getByText('토스')).toBeInTheDocument())
    expect(screen.getByText('백엔드 개발자')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '스크랩' })).toBeInTheDocument()
  })

  it('"내 관심 공고" 토글을 클릭하면 mine=true로 수집 공고를 다시 조회한다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/jobs/stats')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }
      if (url.includes('/auth/me')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ id: 1, email: 't@t.com', nickname: 'tester', createdAt: '2026-01-01', keywords: [] }),
        })
      }
      if (url.includes('/jobs/collected')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '전체 공고' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/jobs/collected'), expect.anything()))

    await user.click(screen.getByRole('button', { name: '내 관심 공고' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('mine=true'), expect.anything()),
    )
  })

  it('scrapedByMe가 true인 수집 공고는 새로고침 후에도 스크랩 완료 상태로 표시된다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        if (url.includes('/jobs/collected')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => [
              {
                id: 1,
                company: '토스',
                title: '백엔드 개발자',
                url: 'https://www.jobkorea.co.kr/Recruit/GI_Read/1',
                source: '잡코리아',
                jobKey: '잡코리아:1',
                createdAt: '2026-01-01T00:00:00Z',
                scrapedByMe: true,
              },
            ],
          })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '전체 공고' }))

    await waitFor(() => expect(screen.getByText('✓ 스크랩 완료')).toBeInTheDocument())
  })

  it('삭제 버튼을 누르면 확인 모달이 뜨고, 확인해야 삭제 요청이 전송된다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/jobs/stats')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }
      if (url.match(/\/jobs\/1$/) && options?.method === 'DELETE') {
        return Promise.resolve({ ok: true, status: 204, json: async () => undefined })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [jobA, jobB] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('카카오')).toBeInTheDocument())

    await user.click(screen.getAllByRole('button', { name: '삭제' })[0])

    const message = screen.getByText('이 공고를 삭제하시겠습니까?')
    expect(message).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.some(([, options]) => (options as RequestInit | undefined)?.method === 'DELETE'),
    ).toBe(false)

    const modal = message.closest('.modal') as HTMLElement
    await user.click(within(modal).getByRole('button', { name: '삭제' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/jobs/1'),
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })
})
