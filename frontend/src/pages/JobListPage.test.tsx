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

  it('검색 범위를 "회사명"으로 바꾸면 서버 재조회 없이 회사명 기준으로만 걸러서 보여준다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string) => {
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
              title: '검색어포함직군',
              url: 'https://example.com/1',
              source: '잡코리아',
              jobKey: '잡코리아:1',
              createdAt: '2026-01-01T00:00:00Z',
            },
          ],
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('토스')).toBeInTheDocument())
    const collectedCallCount = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/jobs/collected')).length

    await user.selectOptions(screen.getByLabelText('검색 범위'), '회사명')
    await user.type(screen.getByPlaceholderText('회사명 또는 포지션 검색'), '검색어포함직군')

    await waitFor(() => expect(screen.queryByText('검색어포함직군')).not.toBeInTheDocument())
    expect(
      fetchMock.mock.calls.filter((call) => String(call[0]).includes('/jobs/collected')).length,
    ).toBe(collectedCallCount)
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

  it('스크랩 버튼을 누르면 스크랩 완료로 바뀌고 성공 메시지를 보여준다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string, options?: RequestInit) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        if (url.match(/\/jobs\/collected\/1\/scrap$/) && options?.method === 'POST') {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ id: 1 }) })
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
                url: 'https://example.com/1',
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

    await user.click(await screen.findByRole('button', { name: '스크랩' }))

    expect(await screen.findByText('내 공고로 가져왔어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '✓ 스크랩 완료' })).toBeDisabled()
  })

  it('공고 불러오기를 누르면 새로 가져온 건수를 안내한다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        if (url.includes('/jobs/collected/crawl')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ loaded: 5 }) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '공고 불러오기' }))

    expect(await screen.findByText('5건 새로 가져왔어요')).toBeInTheDocument()
  })

  it('공고 불러오기 실패 시 에러 메시지를 보여준다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        if (url.includes('/jobs/collected/crawl')) {
          return Promise.resolve({ ok: false, status: 500, json: async () => ({ message: '공고를 불러오지 못했습니다.' }) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '공고 불러오기' }))

    expect(await screen.findByText('공고를 불러오지 못했습니다.')).toBeInTheDocument()
  })

  it('내 공고가 없으면 안내 문구를 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [] })
      }),
    )

    const user = userEvent.setup()
    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])

    expect(
      await screen.findByText('아직 등록한 공고가 없어요 — [+ 공고 추가] 버튼으로 시작해보세요'),
    ).toBeInTheDocument()
  })

  it('상태 select를 바꾸면 상태 변경 요청을 보내고 목록을 새로고침한다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/jobs/stats')) {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }
      if (url.match(/\/jobs\/1$/) && options?.method === 'PATCH') {
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...jobA, status: 'APPLIED' }) })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [jobA] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])
    await waitFor(() => expect(screen.getByText('카카오')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('지원 예정'), '지원함')

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/jobs/1'),
        expect.objectContaining({ method: 'PATCH' }),
      ),
    )
  })

  it('수정 버튼을 누르면 기존 공고 정보로 수정 모달이 열린다', async () => {
    const user = userEvent.setup()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => [jobA] })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])
    await waitFor(() => expect(screen.getByText('카카오')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: '수정' }))

    expect(screen.getByText('공고 수정')).toBeInTheDocument()
    expect(screen.getByDisplayValue('카카오')).toBeInTheDocument()
  })

  it('출처 필터 칩을 클릭하면 서버 재조회 없이 해당 출처만 걸러서 보여준다', async () => {
    const user = userEvent.setup()

    const fetchMock = vi.fn().mockImplementation((url: string) => {
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
              company: '잡코리아공고',
              title: '백엔드',
              url: 'https://example.com/1',
              source: '잡코리아',
              jobKey: '잡코리아:1',
              createdAt: '2026-01-01T00:00:00Z',
            },
            {
              id: 2,
              company: '원티드공고',
              title: '프론트엔드',
              url: 'https://example.com/2',
              source: '원티드',
              jobKey: '원티드:2',
              createdAt: '2026-01-01T00:00:00Z',
            },
          ],
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => [] })
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobListPage onLogout={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('원티드공고')).toBeInTheDocument())
    const collectedCallCount = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/jobs/collected')).length

    await user.click(screen.getByRole('button', { name: '원티드' }))

    await waitFor(() => expect(screen.queryByText('잡코리아공고')).not.toBeInTheDocument())
    expect(screen.getByText('원티드공고')).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.filter((call) => String(call[0]).includes('/jobs/collected')).length,
    ).toBe(collectedCallCount)
  })

  it('불러온 공고가 없으면 안내 문구를 보여준다', async () => {
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

    expect(
      await screen.findByText('아직 불러온 공고가 없어요 — [공고 불러오기]를 눌러 크롤링해보세요'),
    ).toBeInTheDocument()
  })

  it('공고가 30개 넘으면 더보기 버튼으로 추가 공고를 보여준다', async () => {
    const user = userEvent.setup()
    const manyJobs: JobPosting[] = Array.from({ length: 35 }, (_, i) => ({
      id: i + 1,
      companyName: `회사${i + 1}`,
      position: '백엔드 개발자',
      status: 'WISH',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }))

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/stats')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => manyJobs })
      }),
    )

    render(<JobListPage onLogout={vi.fn()} />)

    await user.click(screen.getAllByRole('button', { name: '내 공고' })[0])

    const loadMoreButton = await screen.findByRole('button', { name: '더보기 (30 / 35)' })
    await user.click(loadMoreButton)

    expect(screen.getByText('회사35')).toBeInTheDocument()
  })
})
