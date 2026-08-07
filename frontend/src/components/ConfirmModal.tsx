interface ConfirmModalProps {
  open: boolean
  message: string
  onConfirm: () => void
  onCancel: () => void
}

// 삭제 등 위험한 작업 확인 모달 (브라우저 기본 confirm 대체)
export default function ConfirmModal({ open, message, onConfirm, onCancel }: ConfirmModalProps) {
  if (!open) return null

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <p>{message}</p>
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onCancel}>
            취소
          </button>
          <button type="button" className="danger-button" onClick={onConfirm}>
            삭제
          </button>
        </div>
      </div>
    </div>
  )
}
