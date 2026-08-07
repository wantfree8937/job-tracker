import { useState, type FormEvent } from 'react'
import { createJob, updateJob, previewLink } from '../api'
import { ALL_STATUSES, STATUS_LABEL, type ApplicationStatus, type JobPosting } from '../types'

interface JobFormModalProps {
  job: JobPosting | null
  onClose: () => void
  onSaved: () => void
}

// 공고 추가/수정 겸용 모달
export default function JobFormModal({ job, onClose, onSaved }: JobFormModalProps) {
  const [companyName, setCompanyName] = useState(job?.companyName ?? '')
  const [position, setPosition] = useState(job?.position ?? '')
  const [link, setLink] = useState(job?.link ?? '')
  const [deadline, setDeadline] = useState(job?.deadline ?? '')
  const [status, setStatus] = useState<ApplicationStatus>(job?.status ?? 'WISH')
  const [memo, setMemo] = useState(job?.memo ?? '')
  const [error, setError] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')

    if (!companyName.trim() || !position.trim()) {
      setError('회사명과 포지션은 필수입니다.')
      return
    }

    const input = {
      companyName,
      position,
      link: link || undefined,
      deadline: deadline || undefined,
      status,
      memo: memo || undefined,
    }

    try {
      if (job) {
        await updateJob(job.id, input)
      } else {
        await createJob(input)
      }
      onSaved()
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.')
    }
  }

  // 링크로 회사명/포지션/메모 자동 채우기 (기존 값은 덮어쓰지 않음)
  const handleAutoFill = async () => {
    setError('')
    try {
      const preview = await previewLink(link)
      if (preview.company && !companyName) setCompanyName(preview.company)
      if (preview.position && !position) setPosition(preview.position)
      if (preview.memo && !memo) setMemo(preview.memo)
    } catch (err) {
      setError(err instanceof Error ? err.message : '링크 정보를 가져올 수 없습니다.')
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h2>{job ? '공고 수정' : '공고 추가'}</h2>
        {error && <p className="error-message">{error}</p>}
        <label>
          회사명
          <input type="text" value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
        </label>
        <label>
          포지션
          <input type="text" value={position} onChange={(e) => setPosition(e.target.value)} />
        </label>
        <label>
          링크
          <div className="link-input-row">
            <input type="text" value={link} onChange={(e) => setLink(e.target.value)} />
            <button type="button" className="outline-button" onClick={handleAutoFill} disabled={!link.trim()}>
              자동 채우기
            </button>
          </div>
        </label>
        <label>
          마감일
          <input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
        </label>
        <label>
          상태
          <select value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)}>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          메모
          <textarea value={memo} onChange={(e) => setMemo(e.target.value)} />
        </label>
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            취소
          </button>
          <button type="submit" className="primary-button">
            저장
          </button>
        </div>
      </form>
    </div>
  )
}
