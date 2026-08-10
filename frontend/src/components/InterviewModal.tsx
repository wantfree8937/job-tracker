import { useState, useEffect } from 'react'
import { getInterviewQuestions } from '../api'
import type { JobPosting } from '../types'

interface InterviewModalProps {
  job: JobPosting | null
  onClose: () => void
}

// AI 면접 예상 질문 생성 모달
export default function InterviewModal({ job, onClose }: InterviewModalProps) {
  const [questions, setQuestions] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!job) return
    setIsLoading(true)
    setError('')
    setQuestions([])
    getInterviewQuestions({
      companyName: job.companyName,
      position: job.position,
      region: job.region,
      experience: job.experience,
      industry: job.industry,
      memo: job.memo,
    })
      .then((res) => setQuestions(res.questions))
      .catch((err) => setError(err instanceof Error ? err.message : '질문 생성 중 오류가 발생했습니다.'))
      .finally(() => setIsLoading(false))
  }, [job])

  if (!job) return null

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>AI 면접 연습 — {job.companyName}</h2>
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
          <button type="button" className="outline-button" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}
