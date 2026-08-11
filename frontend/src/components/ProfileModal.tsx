import { useState, useEffect } from 'react'
import { getProfile, saveProfile, getProfileFile, saveProfileFile, deleteProfileFile } from '../api'
import type { ProfileFileResponse } from '../api'

interface ProfileModalProps {
  open: boolean
  onClose: () => void
}

const MAX_LENGTH = 5000

type ProfileTab = 'text' | 'pdf'

// 이력서/포트폴리오 저장 모달 (AI 면접이 참고)
export default function ProfileModal({ open, onClose }: ProfileModalProps) {
  const [tab, setTab] = useState<ProfileTab>('text')
  const [profileText, setProfileText] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isParsing, setIsParsing] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [pdfFile, setPdfFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [savedFile, setSavedFile] = useState<ProfileFileResponse | null>(null)

  useEffect(() => {
    if (!open) return
    setTab('text')
    setError('')
    setMessage('')
    setPdfFile(null)
    setIsLoading(true)
    Promise.all([getProfile(), getProfileFile()])
      .then(([profile, file]) => {
        setProfileText(profile.profileText ?? '')
        setSavedFile(file?.fileName ? file : null)
      })
      .catch((err) => setError(err instanceof Error ? err.message : '이력서를 불러오지 못했습니다.'))
      .finally(() => setIsLoading(false))
  }, [open])

  if (!open) return null

  const isBusy = isLoading || isSaving || isParsing

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

  const handleUploadFile = async () => {
    if (!pdfFile) return
    setError('')
    setMessage('')
    setIsParsing(true)
    try {
      const res = await saveProfileFile(pdfFile)
      setSavedFile(res)
      setPdfFile(null)
      setMessage('파일이 저장되었어요')
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 저장할 수 없어요.')
    } finally {
      setIsParsing(false)
    }
  }

  const handleDownloadFile = async () => {
    setError('')
    try {
      const token = localStorage.getItem('accessToken')
      const res = await fetch('/api/auth/me/profile/file/download', {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      })
      if (!res.ok) throw new Error('파일을 다운로드할 수 없어요.')
      const blob = await res.blob()
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = savedFile?.fileName ?? 'resume'
      link.click()
      URL.revokeObjectURL(link.href)
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 다운로드할 수 없어요.')
    }
  }

  const handleDeleteFile = async () => {
    setError('')
    setMessage('')
    try {
      await deleteProfileFile()
      setSavedFile(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 삭제할 수 없어요.')
    }
  }

  return (
    <div className="modal-overlay" onClick={isBusy ? undefined : onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>내 이력서</h2>
        {isLoading && !profileText && <p>불러오는 중...</p>}
        {error && <p className="error-message">{error}</p>}
        {message && <p className="success-message">{message}</p>}

        <div className="tabs">
          <button type="button" className={tab === 'text' ? 'tab active' : 'tab'} onClick={() => setTab('text')}>
            직접 입력
          </button>
          <button type="button" className={tab === 'pdf' ? 'tab active' : 'tab'} onClick={() => setTab('pdf')}>
            파일 업로드
          </button>
        </div>

        {tab === 'pdf' && (
          <div>
            {savedFile && (
              <div className="saved-file-row">
                <span className="saved-file-name">{savedFile.fileName}</span>
                <button type="button" className="outline-button" onClick={handleDownloadFile}>
                  다운로드
                </button>
                <button type="button" className="outline-button" onClick={handleDeleteFile}>
                  삭제
                </button>
              </div>
            )}
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
              <button type="button" className="outline-button" onClick={handleUploadFile} disabled={isParsing || !pdfFile}>
                {isParsing ? '업로드 중...' : '업로드/저장'}
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
          <button type="button" className="outline-button" onClick={onClose} disabled={isBusy}>
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
