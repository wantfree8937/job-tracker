import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import KeywordsModal from './KeywordsModal'

describe('KeywordsModal', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('직접 입력 후 추가 버튼을 누르면 태그가 생성되고 자동 저장된다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/auth/me/keywords')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({ id: 1, email: 't@t.com', nickname: 'tester', createdAt: '2026-01-01', keywords: ['게임 개발'] }),
          })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={onSaved} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), '게임 개발')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(screen.getByRole('button', { name: '게임 개발 ×' })).toBeInTheDocument()
    await waitFor(() =>
      expect(onSaved).toHaveBeenCalledWith(['게임 개발'], undefined, true),
    )
  })

  it('저장 버튼이 없다', () => {
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(screen.queryByRole('button', { name: '저장' })).not.toBeInTheDocument()
  })

  it('추천 키워드 칩이 없다', () => {
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(screen.queryByRole('button', { name: '백엔드' })).not.toBeInTheDocument()
  })

  it('선택된 키워드의 × 버튼을 누르면 삭제되고 자동 저장된다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/auth/me/keywords')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({ id: 1, email: 't@t.com', nickname: 'tester', createdAt: '2026-01-01', keywords: [] }),
          })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={['게임 개발']} onClose={vi.fn()} onSaved={onSaved} />)

    expect(screen.getByRole('button', { name: '게임 개발 ×' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '게임 개발 ×' }))

    expect(screen.queryByRole('button', { name: '게임 개발 ×' })).not.toBeInTheDocument()
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith([], undefined, true))
  })

  it('키워드로 공고 찾기를 누르면 내 모든 키워드로 searchJobs를 호출하고 결과 메시지를 전달한다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/collect/search')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ keyword: '게임 개발', collected: 22, skipped: 0 }) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={['게임 개발']} onClose={vi.fn()} onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: '키워드로 공고 찾기' }))

    expect(onSaved).toHaveBeenCalledWith(['게임 개발'], '1개 키워드 공고 22건을 가져왔어요!', false)
  })

  it('이미 등록된 공고가 있으면 skipped 건수를 메시지에 포함한다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/collect/search')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ keyword: '청소', collected: 0, skipped: 21 }) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={['청소']} onClose={vi.fn()} onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: '키워드로 공고 찾기' }))

    expect(onSaved).toHaveBeenCalledWith(['청소'], '1개 키워드 공고 0건을 가져왔어요! · 이미 21건 등록돼 있어요', false)
  })

  it('키워드가 없으면 키워드로 공고 찾기 버튼이 비활성화된다', () => {
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(screen.getByRole('button', { name: '키워드로 공고 찾기' })).toBeDisabled()
  })

  it('키워드로 공고 찾기 진행 중에는 버튼이 비활성화되고 로딩 텍스트를 보여준다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    let resolveSearch: (value: { keyword: string; collected: number; skipped: number }) => void
    const searchPromise = new Promise((resolve) => {
      resolveSearch = resolve
    })

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((url: string) => {
        if (url.includes('/jobs/collect/search')) {
          return searchPromise.then((body) => ({ ok: true, status: 200, json: async () => body }))
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={['게임 개발']} onClose={vi.fn()} onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: '키워드로 공고 찾기' }))

    const loadingButton = await screen.findByRole('button', { name: '공고 불러오는 중...' })
    expect(loadingButton).toBeDisabled()
    expect(screen.getByRole('button', { name: '닫기' })).toBeDisabled()

    resolveSearch!({ keyword: '게임 개발', collected: 22, skipped: 0 })

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(screen.getByRole('button', { name: '키워드로 공고 찾기' })).not.toBeDisabled()
  })

  it('2자 미만 입력은 무시하고 태그를 추가하지 않는다', async () => {
    const user = userEvent.setup()
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), 'a')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(screen.queryByRole('button', { name: 'a ×' })).not.toBeInTheDocument()
  })
})
