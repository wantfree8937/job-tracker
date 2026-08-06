// 지원 상태 (백엔드 enum과 동일)
export type ApplicationStatus = 'WISH' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED'

// 상태값 → 한국어 라벨
export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  WISH: '지원 예정',
  APPLIED: '지원함',
  INTERVIEW: '면접',
  OFFER: '합격',
  REJECTED: '불합격',
}

export const ALL_STATUSES: ApplicationStatus[] = ['WISH', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED']

export interface JobPosting {
  id: number
  companyName: string
  position: string
  link?: string
  deadline?: string
  status: ApplicationStatus
  memo?: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface User {
  id: number
  email: string
  nickname: string
  createdAt: string
}

// 상태별 개수 통계 (없는 상태는 응답에서 생략될 수 있음)
export type JobStats = Partial<Record<ApplicationStatus, number>>
