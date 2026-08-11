import { useState } from 'react'
import { getInterviewQuestions } from '../api'
import type { JobPosting } from '../types'

interface InterviewSetupModalProps {
  open: boolean
  onClose: () => void
  jobs: JobPosting[]
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
export default function InterviewSetupModal({ open, onClose, jobs }: InterviewSetupModalProps) {
  const [selectedJobId, setSelectedJobId] = useState('')
  const [topic, setTopic] = useState('MIXED')
  const [difficulty, setDifficulty] = useState('NORMAL')
  const [questions, setQuestions] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  if (!open) return null

  const handleClose = () => {
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

        <div>
          <p className="modal-section-label">면접볼 공고 (선택)</p>
          <select value={selectedJobId} onChange={(e) => setSelectedJobId(e.target.value)}>
            <option value="">공고를 선택하세요 (선택)</option>
            {jobs.map((job) => (
              <option key={job.id} value={job.id}>
                {job.companyName} · {job.position}
              </option>
            ))}
          </select>
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
          <button type="button" className="outline-button" onClick={handleClose}>
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
