import { useState, useEffect } from 'react'
import { getProfile, saveProfile, parseProfileUrl, parseProfilePdf } from '../api'

interface ProfileModalProps {
  open: boolean
  onClose: () => void
}

const MAX_LENGTH = 5000

type ProfileTab = 'text' | 'url' | 'pdf'

// 이력서/포트폴리오 저장 모달 (AI 면접이 참고)
export default function ProfileModal({ open, onClose }: ProfileModalProps) {
  const [tab, setTab] = useState<ProfileTab>('text')
  const [profileText, setProfileText] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isParsing, setIsParsing] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [url, setUrl] = useState('')
  const [pdfFile, setPdfFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)

  useEffect(() => {
    if (!open) return
    setTab('text')
    setError('')
    setMessage('')
    setUrl('')
    setPdfFile(null)
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

  const handleParseUrl = async () => {
    if (!url.trim()) return
    setError('')
    setMessage('')
    setIsParsing(true)
    try {
      const res = await parseProfileUrl(url.trim())
      setProfileText(res.text)
      setTab('text')
    } catch {
      setError('페이지를 읽을 수 없어요 (지원되지 않는 사이트/네트워크)')
    } finally {
      setIsParsing(false)
    }
  }

  const handleParsePdf = async () => {
    if (!pdfFile) return
    setError('')
    setMessage('')
    setIsParsing(true)
    try {
      const res = await parseProfilePdf(pdfFile)
      setProfileText(res.text)
      setTab('text')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'PDF를 변환할 수 없어요.')
    } finally {
      setIsParsing(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>내 이력서</h2>
        {isLoading && <p>불러오는 중...</p>}
        {error && <p className="error-message">{error}</p>}
        {message && <p className="success-message">{message}</p>}

        <div className="tabs">
          <button type="button" className={tab === 'text' ? 'tab active' : 'tab'} onClick={() => setTab('text')}>
            직접 입력
          </button>
          <button type="button" className={tab === 'url' ? 'tab active' : 'tab'} onClick={() => setTab('url')}>
            URL 가져오기
          </button>
          <button type="button" className={tab === 'pdf' ? 'tab active' : 'tab'} onClick={() => setTab('pdf')}>
            PDF 업로드
          </button>
        </div>

        {tab === 'url' && (
          <div className="link-input-row">
            <input
              type="url"
              placeholder="노션 · GitHub · 벨로그 주소만 지원됩니다 (예: https://www.notion.so/...)"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
            />
            <button type="button" className="outline-button" onClick={handleParseUrl} disabled={isParsing || !url.trim()}>
              {isParsing ? '가져오는 중...' : '가져오기'}
            </button>
          </div>
        )}

        {tab === 'pdf' && (
          <div>
            <label
              className={isDragging ? 'drop-zone dragging' : 'drop-zone'}
              onDragOver={(e) => {
                e.preventDefault()
                setIsDragging(true)
              }}
              onDragLeave={(e) => {
                e.preventDefault()
                setIsDragging(false)
              }}
              onDrop={(e) => {
                e.preventDefault()
                setIsDragging(false)
                setPdfFile(e.dataTransfer.files?.[0] ?? null)
              }}
            >
              <input
                type="file"
                accept=".pdf,.ppt,.pptx"
                onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)}
              />
              <span className="drop-zone-title">{pdfFile ? pdfFile.name : '클릭하거나 파일을 끌어다 놓으세요'}</span>
              <span className="drop-zone-hint">PDF · PPT · PPTX (최대 10MB)</span>
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={handleParsePdf} disabled={isParsing || !pdfFile}>
                {isParsing ? '변환 중...' : '변환'}
              </button>
            </div>
          </div>
        )}

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
