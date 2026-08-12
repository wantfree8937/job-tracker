import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import InterviewSetupModal from './InterviewSetupModal'
import { getInterviewQuestions } from '../api'
import type { JobPosting } from '../types'

vi.mock('../api', () => ({
  getInterviewQuestions: vi.fn(),
}))

const jobA: JobPosting = {
  id: 1,
  companyName: '카카오',
  position: '백엔드 개발자',
  status: 'WISH',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('InterviewSetupModal', () => {
  beforeEach(() => {
    vi.mocked(getInterviewQuestions).mockReset()
  })

  it('open이 false면 아무것도 렌더링하지 않는다', () => {
    const { container } = render(
      <InterviewSetupModal open={false} onClose={vi.fn()} jobs={[]} profileText={null} hasResumeFile={false} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('공고 선택 드롭다운을 열고 공고를 선택할 수 있다', async () => {
    const user = userEvent.setup()
    render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[jobA]} profileText={null} hasResumeFile={false} />,
    )

    await user.click(screen.getByRole('button', { name: /공고를 선택하세요/ }))
    expect(screen.getByRole('listbox')).toBeInTheDocument()

    await user.click(screen.getByRole('option', { name: '카카오 · 백엔드 개발자' }))

    expect(screen.getByRole('button', { name: /카카오 · 백엔드 개발자/ })).toBeInTheDocument()
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('질문 유형과 난이도 칩을 선택할 수 있다', async () => {
    const user = userEvent.setup()
    render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[]} profileText={null} hasResumeFile={false} />,
    )

    const motiveChip = screen.getByRole('button', { name: '지원동기·인성 위주' })
    await user.click(motiveChip)
    expect(motiveChip).toHaveClass('active')

    const easyChip = screen.getByRole('button', { name: '쉬움' })
    await user.click(easyChip)
    expect(easyChip).toHaveClass('active')
  })

  it('시작하기를 누르면 로딩 후 질문 목록과 이력서 반영 여부를 보여준다', async () => {
    let resolveQuestions: (value: { questions: string[]; usedResume: boolean }) => void
    const questionsPromise = new Promise<{ questions: string[]; usedResume: boolean }>((resolve) => {
      resolveQuestions = resolve
    })
    vi.mocked(getInterviewQuestions).mockReturnValue(questionsPromise)
    const user = userEvent.setup()

    render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[jobA]} profileText="경력 5년" hasResumeFile={false} />,
    )

    await user.click(screen.getByRole('button', { name: '시작하기' }))

    expect(screen.getByText('질문 생성 중...')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '닫기' })).toBeDisabled()

    resolveQuestions!({
      questions: ['자기소개를 해주세요', '가장 기억에 남는 프로젝트는?'],
      usedResume: true,
    })

    expect(await screen.findByText('자기소개를 해주세요')).toBeInTheDocument()
    expect(screen.getByText('가장 기억에 남는 프로젝트는?')).toBeInTheDocument()
    expect(screen.getByText('📄 저장된 이력서를 반영해 질문을 만들었어요')).toBeInTheDocument()
  })

  it('usedResume이 false면 미반영 안내를 보여준다', async () => {
    vi.mocked(getInterviewQuestions).mockResolvedValue({
      questions: ['질문1'],
      usedResume: false,
    })
    const user = userEvent.setup()

    render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[]} profileText="경력 5년" hasResumeFile={false} />,
    )

    await user.click(screen.getByRole('button', { name: '시작하기' }))

    expect(
      await screen.findByText(/이력서\/포트폴리오가 반영되지 않았어요/),
    ).toBeInTheDocument()
  })

  it('질문 생성 실패 시 에러 메시지를 보여준다', async () => {
    vi.mocked(getInterviewQuestions).mockRejectedValue(new Error('질문 생성 실패'))
    const user = userEvent.setup()

    render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[]} profileText={null} hasResumeFile={false} />,
    )

    await user.click(screen.getByRole('button', { name: '시작하기' }))

    expect(await screen.findByText('질문 생성 실패')).toBeInTheDocument()
  })

  it('이력서가 없으면 일반 질문 안내를, 파일만 있으면 파일 참고 안내를 보여준다', () => {
    const { rerender } = render(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[]} profileText={null} hasResumeFile={false} />,
    )
    expect(screen.getByText(/이력서가 아직 없어요/)).toBeInTheDocument()

    rerender(
      <InterviewSetupModal open onClose={vi.fn()} jobs={[]} profileText={null} hasResumeFile />,
    )
    expect(screen.getByText(/이력서 파일이 있어요/)).toBeInTheDocument()
  })
})
