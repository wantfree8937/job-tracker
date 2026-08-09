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
  keywords: string[]
}

// 관심 분야 후보 목록
export const KEYWORD_OPTIONS = [
  '안드로이드',
  'iOS',
  '앱 개발',
  '백엔드',
  '프론트엔드',
  '데이터 분석',
  '서버',
  '게임',
  'AI',
]

// 상태별 개수 통계 (없는 상태는 응답에서 생략될 수 있음)
export type JobStats = Partial<Record<ApplicationStatus, number>>

// 크롤러가 수집한 공고 (모든 사용자 공유)
export interface CollectedJob {
  id: number
  company: string
  title: string
  url: string
  source: string
  jobKey: string
  createdAt: string
  scrapedByMe: boolean
  region?: string
  experience?: string
  industry?: string
}

// 링크 미리보기 결과 (못 찾은 필드는 null)
export interface LinkPreview {
  company: string | null
  position: string | null
  memo: string | null
}
