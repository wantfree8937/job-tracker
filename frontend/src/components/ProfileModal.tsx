import { useState, useEffect } from 'react'
import { saveProfile, saveProfileFile, deleteProfileFile } from '../api'
import type { ProfileFileResponse } from '../api'

const MAX_FILES = 3

interface ProfileModalProps {
  open: boolean
  onClose: () => void
  initialProfileText: string
  initialFiles: ProfileFileResponse[]
  onProfileSaved: (profileText: string) => void
  onFilesChanged: (files: ProfileFileResponse[]) => void
}

const MAX_LENGTH = 5000

type ProfileTab = 'text' | 'pdf'

// 이력서/포트폴리오 저장 모달 (AI 면접이 참고) — 부모가 캐시한 초기값으로 즉시 표시
export default function ProfileModal({
  open,
  onClose,
  initialProfileText,
  initialFiles,
  onProfileSaved,
  onFilesChanged,
}: ProfileModalProps) {
  const [tab, setTab] = useState<ProfileTab>('text')
  const [profileText, setProfileText] = useState(initialProfileText)
  const [isSaving, setIsSaving] = useState(false)
  const [isParsing, setIsParsing] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [pdfFile, setPdfFile] = useState<File | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [savedFiles, setSavedFiles] = useState<ProfileFileResponse[]>(initialFiles)

  useEffect(() => {
    if (!open) return
    setTab('text')
    setError('')
    setMessage('')
    setPdfFile(null)
  }, [open])

  if (!open) return null

  const isBusy = isSaving || isParsing

  const handleSave = async () => {
    setError('')
    setMessage('')
    setIsSaving(true)
    try {
      await saveProfile(profileText)
      setMessage('이력서가 저장되었어요')
      onProfileSaved(profileText)
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
      const next = [...savedFiles, res]
      setSavedFiles(next)
      setPdfFile(null)
      setMessage(`파일이 저장되었어요 (${next.length}/${MAX_FILES})`)
      onFilesChanged(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 저장할 수 없어요.')
    } finally {
      setIsParsing(false)
    }
  }

  const handleDownloadFile = async (file: ProfileFileResponse) => {
    setError('')
    try {
      const token = localStorage.getItem('accessToken')
      const res = await fetch(`/api/auth/me/profile/file/${file.id}/download`, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      })
      if (!res.ok) throw new Error('파일을 다운로드할 수 없어요.')
      const blob = await res.blob()
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = file.fileName ?? 'resume'
      link.click()
      URL.revokeObjectURL(link.href)
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 다운로드할 수 없어요.')
    }
  }

  const handleDeleteFile = async (fileId: number) => {
    setError('')
    setMessage('')
    try {
      await deleteProfileFile(fileId)
      const next = savedFiles.filter((f) => f.id !== fileId)
      setSavedFiles(next)
      onFilesChanged(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : '파일을 삭제할 수 없어요.')
    }
  }

  return (
    <div className="modal-overlay" onClick={isBusy ? undefined : onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>내 이력서</h2>
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
            {savedFiles.length > 0 ? (
              savedFiles.map((file) => (
                <div key={file.id} className="saved-file-row">
                  <span className="saved-file-name">{file.fileName}</span>
                  <button type="button" className="outline-button" onClick={() => handleDownloadFile(file)}>
                    다운로드
                  </button>
                  <button type="button" className="outline-button" onClick={() => handleDeleteFile(file.id)}>
                    삭제
                  </button>
                </div>
              ))
            ) : (
              <p className="modal-section-label">저장된 파일이 없어요</p>
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
              <button
                type="button"
                className="outline-button"
                onClick={handleUploadFile}
                disabled={isParsing || !pdfFile || savedFiles.length >= MAX_FILES}
              >
                {isParsing ? '업로드 중...' : `업로드/저장 (${savedFiles.length}/${MAX_FILES})`}
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
          <button type="button" className="primary-button" onClick={handleSave} disabled={isSaving}>
            {isSaving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}
