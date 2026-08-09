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

const jobWithMeta: JobPosting = {
  id: 3,
  companyName: '라인',
  position: '백엔드 개발자',
  status: 'WISH',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  region: '서울 송파구',
  experience: '신입',
  industry: '광고·홍보·전시',
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

    // 내 공고 탭으로 이동 후 상태 필터 칩 테스트
    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])
    await waitFor(() => expect(screen.getByText('카카오')).toBeInTheDocument())
    expect(screen.getByText('네이버')).toBeInTheDocument()

    // "지원함" 필터 칩 클릭 → 네이버만 남는다
    await user.click(screen.getByRole('button', { name: '지원함' }))

    await waitFor(() => expect(screen.queryByText('카카오')).not.toBeInTheDocument())
    expect(screen.getByText('네이버')).toBeInTheDocument()
  })

  it('내 공고 카드에 지역·경력·업종이 있으면 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [jobWithMeta] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await screen.findAllByRole('button', { name: '내 공고' })
    const user = userEvent.setup()
    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])

    await waitFor(() => expect(screen.getByText('서울 송파구 · 신입 · 광고·홍보·전시')).toBeInTheDocument())
  })

  it('전체 공고 탭으로 전환하면 수집 공고 목록을 조회해 보여준다', async () => {
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

    // 초기 화면이 "전체 공고" 탭이므로 수집 공고가 바로 보인다
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

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/jobs/collected'), expect.anything()))

    await user.click(screen.getByRole('button', { name: '내 관심 공고' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('mine=true'), expect.anything()),
    )
  })

  it('scrapedByMe가 true인 수집 공고는 새로고침 후에도 스크랩 완료 상태로 표시된다', async () => {
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

    await waitFor(() => expect(screen.getByText('✓ 스크랩 완료')).toBeInTheDocument())
  })

  it('스크랩 완료 공고는 목록 아래로 정렬되고 흐리게 표시된다', async () => {
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
                company: '먼저스크랩',
                title: '스크랩한 공고',
                url: 'https://example.com/1',
                source: '잡코리아',
                jobKey: '잡코리아:1',
                createdAt: '2026-01-02T00:00:00Z',
                scrapedByMe: true,
              },
              {
                id: 2,
                company: '나중일반',
                title: '아직 안 본 공고',
                url: 'https://example.com/2',
                source: '원티드',
                jobKey: '원티드:2',
                createdAt: '2026-01-01T00:00:00Z',
                scrapedByMe: false,
              },
            ],
          })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    const { container } = render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('나중일반')).toBeInTheDocument())

    const cards = container.querySelectorAll('.job-card')
    expect(cards[0].textContent).toContain('나중일반')
    expect(cards[1].textContent).toContain('먼저스크랩')
    expect(cards[1].className).toContain('job-card-scraped')
    expect(cards[0].className).not.toContain('job-card-scraped')
  })

  it('전체 공고 도구 모음에 정렬 기준 라벨을 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('정렬: 최신 수집순')).toBeInTheDocument())
  })

  it('검색 범위를 "회사명"으로 바꾸면 searchField=company로 수집 공고를 조회한다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/jobs/stats')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/jobs/collected'), expect.anything()))

    await user.selectOptions(screen.getByLabelText('검색 범위'), '회사명')

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('searchField=company'), expect.anything()),
    )
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

    // 삭제는 내 공고 탭 기능 — 탭 전환 후 확인
    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])
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
