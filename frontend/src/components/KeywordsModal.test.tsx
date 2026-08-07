import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import KeywordsModal from './KeywordsModal'

describe('KeywordsModal', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('직접 입력 후 추가 버튼을 누르면 태그가 생성된다', async () => {
    const user = userEvent.setup()
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), '게임 개발')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(screen.getByRole('button', { name: '게임 개발 ×' })).toBeInTheDocument()
  })

  it('저장하고 공고 불러오기를 누르면 새 키워드로 searchJobs를 호출하고 결과 메시지를 전달한다', async () => {
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
        if (url.includes('/jobs/collect/search')) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ keyword: '게임 개발', collected: 22, skipped: 0 }) })
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={onSaved} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), '게임 개발')
    await user.click(screen.getByRole('button', { name: '추가' }))
    await user.click(screen.getByRole('button', { name: '저장하고 공고 불러오기' }))

    expect(onSaved).toHaveBeenCalledWith(['게임 개발'], '게임 개발 공고 22건을 가져왔어요!')
  })

  it('저장하고 공고 불러오기 진행 중에는 버튼이 비활성화되고 로딩 텍스트를 보여준다', async () => {
    const user = userEvent.setup()
    const onSaved = vi.fn()

    let resolveSearch: (value: { keyword: string; collected: number; skipped: number }) => void
    const searchPromise = new Promise((resolve) => {
      resolveSearch = resolve
    })

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
        if (url.includes('/jobs/collect/search')) {
          return searchPromise.then((body) => ({ ok: true, status: 200, json: async () => body }))
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
      }),
    )

    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={onSaved} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), '게임 개발')
    await user.click(screen.getByRole('button', { name: '추가' }))
    await user.click(screen.getByRole('button', { name: '저장하고 공고 불러오기' }))

    const loadingButton = await screen.findByRole('button', { name: '공고 불러오는 중...' })
    expect(loadingButton).toBeDisabled()
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()

    resolveSearch!({ keyword: '게임 개발', collected: 22, skipped: 0 })

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(screen.getByRole('button', { name: '저장하고 공고 불러오기' })).not.toBeDisabled()
  })

  it('2자 미만 입력은 무시하고 태그를 추가하지 않는다', async () => {
    const user = userEvent.setup()
    render(<KeywordsModal currentKeywords={[]} onClose={vi.fn()} onSaved={vi.fn()} />)

    await user.type(screen.getByPlaceholderText('직접 입력 (2~20자)'), 'a')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(screen.queryByRole('button', { name: 'a ×' })).not.toBeInTheDocument()
  })
})
