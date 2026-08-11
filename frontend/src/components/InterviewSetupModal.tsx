import { useEffect, useRef, useState } from 'react'
import { getInterviewQuestions } from '../api'
import type { JobPosting } from '../types'

interface InterviewSetupModalProps {
  open: boolean
  onClose: () => void
  jobs: JobPosting[]
  profileText: string | null
  hasResumeFile: boolean
}

const TOPICS = [
  { value: 'TECHNICAL', label: '기술 질문 위주' },
  { value: 'PORTFOLIO', label: '포트폴리오 질문 위주' },
  { value: 'MIXED', label: '혼합' },
]

const DIFFICULTIES = [
  { value: 'EASY', label: '쉬움' },
  { value: 'NORMAL', label: '보통' },
  { value: 'HARD', label: '어려움' },
]

// AI 면접 설정(유형/난이도) 선택 → 예상 질문 생성 모달
export default function InterviewSetupModal({ open, onClose, jobs, profileText, hasResumeFile }: InterviewSetupModalProps) {
  const [selectedJobId, setSelectedJobId] = useState('')
  const [topic, setTopic] = useState('MIXED')
  const [difficulty, setDifficulty] = useState('NORMAL')
  const [questions, setQuestions] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [isJobSelectOpen, setIsJobSelectOpen] = useState(false)
  const jobSelectRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isJobSelectOpen) return
    const handleOutsideClick = (e: MouseEvent) => {
      if (jobSelectRef.current && !jobSelectRef.current.contains(e.target as Node)) {
        setIsJobSelectOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutsideClick)
    return () => document.removeEventListener('mousedown', handleOutsideClick)
  }, [isJobSelectOpen])

  if (!open) return null

  const selectedJob = jobs.find((j) => j.id === Number(selectedJobId))

  const handleClose = () => {
    if (isLoading) return
    setQuestions([])
    setError('')
    onClose()
  }

  const handleStart = () => {
    const selected = jobs.find((j) => j.id === Number(selectedJobId))
    setIsLoading(true)
    setError('')
    setQuestions([])
    getInterviewQuestions({
      companyName: selected?.companyName || undefined,
      position: selected?.position || undefined,
      region: selected?.region || undefined,
      experience: selected?.experience || undefined,
      industry: selected?.industry || undefined,
      memo: selected?.memo || undefined,
      topic,
      difficulty,
    })
      .then((res) => setQuestions(res.questions))
      .catch((err) => setError(err instanceof Error ? err.message : '질문 생성 중 오류가 발생했습니다.'))
      .finally(() => setIsLoading(false))
  }

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>AI 면접 연습</h2>

        {profileText?.trim() ? (
          <p className="profile-hint">📄 저장된 이력서를 참고해 질문을 만듭니다</p>
        ) : hasResumeFile ? (
          <p className="profile-hint">📎 이력서 파일이 있어요 — 파일 내용도 참고합니다</p>
        ) : (
          <p className="profile-hint profile-hint-empty">이력서가 아직 없어요 — 일반 질문으로 진행됩니다 ([내 이력서]에서 등록 가능)</p>
        )}

        <div>
          <p className="modal-section-label">면접볼 공고 (선택)</p>
          <div className="interview-job-select-wrap" ref={jobSelectRef}>
            <button
              type="button"
              className="interview-job-select"
              onClick={() => setIsJobSelectOpen((v) => !v)}
            >
              <span>{selectedJob ? `${selectedJob.companyName} · ${selectedJob.position}` : '공고를 선택하세요 (선택)'}</span>
              <span className="interview-job-select-arrow">▼</span>
            </button>
            {isJobSelectOpen && (
              <div className="interview-job-select-list" role="listbox">
                <div
                  role="option"
                  aria-selected={selectedJobId === ''}
                  className={selectedJobId === '' ? 'interview-job-select-option active' : 'interview-job-select-option'}
                  onClick={() => {
                    setSelectedJobId('')
                    setIsJobSelectOpen(false)
                  }}
                >
                  공고를 선택하세요 (선택)
                </div>
                {jobs.map((job) => (
                  <div
                    key={job.id}
                    role="option"
                    aria-selected={Number(selectedJobId) === job.id}
                    className={Number(selectedJobId) === job.id ? 'interview-job-select-option active' : 'interview-job-select-option'}
                    onClick={() => {
                      setSelectedJobId(String(job.id))
                      setIsJobSelectOpen(false)
                    }}
                  >
                    {job.companyName} · {job.position}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div>
          <p className="modal-section-label">질문 유형</p>
          <div className="status-filters">
            {TOPICS.map((t) => (
              <button
                key={t.value}
                type="button"
                className={topic === t.value ? 'chip active' : 'chip'}
                onClick={() => setTopic(t.value)}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <p className="modal-section-label">난이도</p>
          <div className="status-filters">
            {DIFFICULTIES.map((d) => (
              <button
                key={d.value}
                type="button"
                className={difficulty === d.value ? 'chip active' : 'chip'}
                onClick={() => setDifficulty(d.value)}
              >
                {d.label}
              </button>
            ))}
          </div>
        </div>

        {isLoading && <p>질문 생성 중...</p>}
        {error && <p className="error-message">{error}</p>}
        {questions.length > 0 && (
          <ol className="interview-question-list">
            {questions.map((q, i) => (
              <li key={i}>{q}</li>
            ))}
          </ol>
        )}

        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={handleClose} disabled={isLoading}>
            닫기
          </button>
          <button type="button" className="primary-button" onClick={handleStart} disabled={isLoading}>
            {isLoading ? '생성 중...' : '시작하기'}
          </button>
        </div>
      </div>
    </div>
  )
}
