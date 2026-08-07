import { useState } from 'react'
import { updateKeywords } from '../api'
import { KEYWORD_OPTIONS } from '../types'

interface KeywordsModalProps {
  currentKeywords: string[]
  onClose: () => void
  onSaved: (keywords: string[]) => void
}

// 관심 분야 설정 모달
export default function KeywordsModal({ currentKeywords, onClose, onSaved }: KeywordsModalProps) {
  const [selected, setSelected] = useState<Set<string>>(new Set(currentKeywords))
  const [error, setError] = useState('')

  const toggle = (keyword: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(keyword)) {
        next.delete(keyword)
      } else {
        next.add(keyword)
      }
      return next
    })
  }

  const handleSave = async () => {
    setError('')
    try {
      const user = await updateKeywords(Array.from(selected))
      onSaved(user.keywords)
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.')
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>관심 분야 설정</h2>
        {error && <p className="error-message">{error}</p>}
        <div className="status-filters">
          {KEYWORD_OPTIONS.map((keyword) => (
            <button
              key={keyword}
              type="button"
              className={selected.has(keyword) ? 'chip active' : 'chip'}
              onClick={() => toggle(keyword)}
            >
              {keyword}
            </button>
          ))}
        </div>
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            취소
          </button>
          <button type="button" className="primary-button" onClick={handleSave}>
            저장
          </button>
        </div>
      </div>
    </div>
  )
}
