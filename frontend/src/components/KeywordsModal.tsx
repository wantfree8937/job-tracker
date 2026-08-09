import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { updateKeywords, searchJobs } from '../api'

interface KeywordsModalProps {
  currentKeywords: string[]
  onClose: () => void
  onSaved: (keywords: string[], message?: string, keepOpen?: boolean) => void
}

// 관심 분야 설정 모달 (추가/삭제 시 자동 저장)
export default function KeywordsModal({ currentKeywords, onClose, onSaved }: KeywordsModalProps) {
  const [selected, setSelected] = useState<Set<string>>(new Set(currentKeywords))
  const [customInput, setCustomInput] = useState('')
  const [error, setError] = useState('')
  const [isCollecting, setIsCollecting] = useState(false)

  // 에러도 맨 위 빨간 토스트로 (3초 후 자동 제거 — 모달 레이아웃 밀림 없음)
  useEffect(() => {
    if (!error) return
    const timer = setTimeout(() => setError(''), 3000)
    return () => clearTimeout(timer)
  }, [error])

  const autoSave = async (keywords: string[]) => {
    setError('')
    try {
      const user = await updateKeywords(keywords)
      onSaved(user.keywords, '관심 분야를 저장했어요', true) // 맨 위 토스트로 안내
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.')
    }
  }

  const updateSelected = (mutate: (next: Set<string>) => void) => {
    const next = new Set(selected)
    mutate(next)
    setSelected(next)
    autoSave(Array.from(next))
  }

  const removeKeyword = (keyword: string) => {
    updateSelected((next) => next.delete(keyword))
  }

  const addCustomKeyword = () => {
    const value = customInput.trim()
    if (value.length < 2 || value.length > 20 || !/^[가-힣a-zA-Z0-9\s]+$/.test(value)) {
      setError('키워드는 2~20자, 한글/영문/숫자/공백만 사용할 수 있어요')
      return
    }
    if (selected.has(value)) return
    updateSelected((next) => next.add(value))
    setCustomInput('')
  }

  const handleCustomKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addCustomKeyword()
    }
  }

  const handleCollectAll = async () => {
    setError('')
    const keywords = Array.from(selected)
    setIsCollecting(true)
    try {
      const results = await Promise.all(keywords.map((k) => searchJobs(k)))
      const totalCollected = results.reduce((sum, r) => sum + r.collected, 0)
      const totalSkipped = results.reduce((sum, r) => sum + r.skipped, 0)
      const skippedText = totalSkipped > 0 ? ` · 이미 ${totalSkipped}건 등록돼 있어요` : ''
      onSaved(keywords, `${keywords.length}개 키워드 공고 ${totalCollected}건을 가져왔어요!${skippedText}`, false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '공고 검색에 실패했습니다.')
    } finally {
      setIsCollecting(false)
    }
  }

  const selectedKeywords = Array.from(selected)

  return (
    <div className="modal-overlay" onClick={isCollecting ? undefined : onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>관심 분야 설정</h2>
        {error &&
          createPortal(<div className="toast toast-error">{error}</div>, document.body)}

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

        {selectedKeywords.length > 0 && (
          <div className="status-filters">
            {selectedKeywords.map((keyword) => (
              <button key={keyword} type="button" className="chip active" onClick={() => removeKeyword(keyword)}>
                {keyword} ×
              </button>
            ))}
          </div>
        )}

        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose} disabled={isCollecting}>
            닫기
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={handleCollectAll}
            disabled={isCollecting || selectedKeywords.length === 0}
          >
            {isCollecting ? '공고 불러오는 중...' : '키워드로 공고 찾기'}
          </button>
        </div>
      </div>
    </div>
  )
}
