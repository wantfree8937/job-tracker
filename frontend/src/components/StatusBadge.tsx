import { STATUS_LABEL, type ApplicationStatus } from '../types'

// 상태별 배지 색상 클래스
const STATUS_CLASS: Record<ApplicationStatus, string> = {
  WISH: 'badge-wish',
  APPLIED: 'badge-applied',
  INTERVIEW: 'badge-interview',
  OFFER: 'badge-offer',
  REJECTED: 'badge-rejected',
}

export default function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge ${STATUS_CLASS[status]}`}>{STATUS_LABEL[status]}</span>
}
