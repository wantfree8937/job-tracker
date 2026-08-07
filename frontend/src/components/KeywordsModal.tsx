import { useState } from 'react'
import { updateKeywords, searchJobs } from '../api'
import { KEYWORD_OPTIONS } from '../types'

interface KeywordsModalProps {
  currentKeywords: string[]
  onClose: () => void
  onSaved: (keywords: string[], message?: string) => void
}

// 관심 분야 설정 모달
export default function KeywordsModal({ currentKeywords, onClose, onSaved }: KeywordsModalProps) {
  const [selected, setSelected] = useState<Set<string>>(new Set(currentKeywords))
  const [customInput, setCustomInput] = useState('')
  const [error, setError] = useState('')
  const [isSearching, setIsSearching] = useState(false)

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

  const addCustomKeyword = () => {
    const value = customInput.trim()
    if (value.length < 2 || value.length > 20) return
    if (selected.has(value)) return
    setSelected((prev) => new Set(prev).add(value))
    setCustomInput('')
  }

  const handleCustomKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addCustomKeyword()
    }
  }

  const handleSave = async (withSearch: boolean) => {
    setError('')
    const keywords = Array.from(selected)
    if (withSearch) setIsSearching(true)
    try {
      const user = await updateKeywords(keywords)
      const newKeywords = keywords.filter((k) => !currentKeywords.includes(k))

      if (!withSearch || newKeywords.length === 0) {
        onSaved(user.keywords)
        return
      }

      try {
        const results = await Promise.all(newKeywords.map((k) => searchJobs(k)))
        const totalCollected = results.reduce((sum, r) => sum + r.collected, 0)
        onSaved(user.keywords, `${newKeywords.join(', ')} 공고 ${totalCollected}건을 가져왔어요!`)
      } catch (searchErr) {
        onSaved(user.keywords, searchErr instanceof Error ? searchErr.message : '공고 검색에 실패했습니다.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.')
    } finally {
      setIsSearching(false)
    }
  }

  const customKeywords = Array.from(selected).filter((k) => !KEYWORD_OPTIONS.includes(k))

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

        <div className="link-input-row">
          <input
            type="text"
            placeholder="직접 입력 (2~20자)"
            value={customInput}
            onChange={(e) => setCustomInput(e.target.value)}
            onKeyDown={handleCustomKeyDown}
          />
          <button type="button" className="outline-button" onClick={addCustomKeyword}>
            추가
          </button>
        </div>

        {customKeywords.length > 0 && (
          <div className="status-filters">
            {customKeywords.map((keyword) => (
              <button key={keyword} type="button" className="chip active" onClick={() => toggle(keyword)}>
                {keyword} ×
              </button>
            ))}
          </div>
        )}

        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose} disabled={isSearching}>
            취소
          </button>
          <button type="button" className="outline-button" onClick={() => handleSave(false)} disabled={isSearching}>
            저장
          </button>
          <button type="button" className="primary-button" onClick={() => handleSave(true)} disabled={isSearching}>
            {isSearching ? '공고 불러오는 중...' : '저장하고 공고 불러오기'}
          </button>
        </div>
      </div>
    </div>
  )
}
