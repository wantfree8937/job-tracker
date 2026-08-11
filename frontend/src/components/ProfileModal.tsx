import { useState, useEffect } from 'react'
import { getProfile, saveProfile } from '../api'

interface ProfileModalProps {
  open: boolean
  onClose: () => void
}

const MAX_LENGTH = 5000

// 이력서/포트폴리오 저장 모달 (AI 면접이 참고)
export default function ProfileModal({ open, onClose }: ProfileModalProps) {
  const [profileText, setProfileText] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!open) return
    setError('')
    setMessage('')
    setIsLoading(true)
    getProfile()
      .then((res) => setProfileText(res.profileText ?? ''))
      .catch((err) => setError(err instanceof Error ? err.message : '이력서를 불러오지 못했습니다.'))
      .finally(() => setIsLoading(false))
  }, [open])

  if (!open) return null

  const handleSave = async () => {
    setError('')
    setMessage('')
    setIsSaving(true)
    try {
      await saveProfile(profileText)
      setMessage('이력서가 저장되었어요')
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>내 이력서</h2>
        {isLoading && <p>불러오는 중...</p>}
        {error && <p className="error-message">{error}</p>}
        {message && <p className="success-message">{message}</p>}
        <label>
          이력서/포트폴리오 내용 (자유 형식 — AI 면접이 참고합니다)
          <textarea
            value={profileText}
            onChange={(e) => setProfileText(e.target.value)}
            maxLength={MAX_LENGTH}
            rows={12}
          />
        </label>
        <p className="modal-section-label">{profileText.length} / {MAX_LENGTH}자</p>
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            닫기
          </button>
          <button type="button" className="primary-button" onClick={handleSave} disabled={isSaving || isLoading}>
            {isSaving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}
