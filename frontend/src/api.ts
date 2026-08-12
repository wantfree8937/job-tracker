import type { AuthResponse, JobPosting, ApplicationStatus, JobStats, User, CollectedJob, LinkPreview } from './types'

const BASE_URL = '/api'

interface ApiErrorBody {
  message: string
}

// 공통 fetch 래퍼: 토큰 첨부, 401 처리, 에러 메시지 파싱
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('accessToken')
  // FormData는 Content-Type을 지정하면 안 됨 (브라우저가 boundary 포함해 자동 설정)
  const headers: Record<string, string> = {
    ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers as Record<string, string> | undefined),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers })

  if (res.status === 401) {
    localStorage.removeItem('accessToken')
    window.location.href = '/'
    throw new Error('로그인이 필요합니다.')
  }

  if (!res.ok) {
    const body: ApiErrorBody = await res
      .json()
      .catch(() => ({ message: '요청 처리 중 오류가 발생했습니다.' }))
    throw new Error(body.message)
  }

  if (res.status === 204) {
    return undefined as T
  }

  return res.json()
}

export function signup(email: string, password: string, nickname: string): Promise<User> {
  return request('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ email, password, nickname }),
  })
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const data = await request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  localStorage.setItem('accessToken', data.accessToken)
  return data
}

export function me(): Promise<User> {
  return request('/auth/me')
}

export function updateKeywords(keywords: string[]): Promise<User> {
  return request('/auth/me/keywords', { method: 'PUT', body: JSON.stringify({ keywords }) })
}

export function getJobs(params: { status?: ApplicationStatus; keyword?: string } = {}): Promise<JobPosting[]> {
  const query = new URLSearchParams()
  if (params.status) query.set('status', params.status)
  if (params.keyword) query.set('keyword', params.keyword)
  const qs = query.toString()
  return request(`/jobs${qs ? `?${qs}` : ''}`)
}

export function getStats(): Promise<JobStats> {
  return request('/jobs/stats')
}

export interface JobInput {
  companyName: string
  position: string
  link?: string
  deadline?: string
  status?: ApplicationStatus
  memo?: string
  region?: string
  experience?: string
  industry?: string
}

export function createJob(input: JobInput): Promise<JobPosting> {
  return request('/jobs', { method: 'POST', body: JSON.stringify(input) })
}

export function updateJob(id: number, input: Partial<JobInput>): Promise<JobPosting> {
  return request(`/jobs/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteJob(id: number): Promise<void> {
  return request(`/jobs/${id}`, { method: 'DELETE' })
}

export interface CollectedJobLoadResult {
  loaded: number
  skipped: number
}

export function loadCollectedJobs(): Promise<CollectedJobLoadResult> {
  return request('/jobs/collected/load', { method: 'POST' })
}

export function crawlCollected(keywords?: string[]): Promise<CollectedJobLoadResult> {
  return request('/jobs/collected/crawl', {
    method: 'POST',
    body: JSON.stringify(keywords && keywords.length > 0 ? { keywords } : {}),
  })
}

export type CollectedJobSearchField = 'all' | 'company' | 'title'

export function getCollectedJobs(
  params: { keyword?: string; source?: string; mine?: boolean; searchField?: CollectedJobSearchField } = {},
): Promise<CollectedJob[]> {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.source) query.set('source', params.source)
  if (params.mine) query.set('mine', 'true')
  if (params.searchField && params.searchField !== 'all') query.set('searchField', params.searchField)
  const qs = query.toString()
  return request(`/jobs/collected${qs ? `?${qs}` : ''}`)
}

export function scrapCollectedJob(id: number): Promise<JobPosting> {
  return request(`/jobs/collected/${id}/scrap`, { method: 'POST' })
}

export function previewLink(link: string): Promise<LinkPreview> {
  return request('/jobs/preview', { method: 'POST', body: JSON.stringify({ link }) })
}

export interface JobSearchResult {
  keyword: string
  collected: number
  skipped: number
}

export function searchJobs(keyword: string): Promise<JobSearchResult> {
  return request('/jobs/collect/search', { method: 'POST', body: JSON.stringify({ keyword }) })
}

export interface InterviewQuestionRequest {
  topic?: string // 'TECHNICAL' | 'PORTFOLIO' | 'MIXED'
  difficulty?: string // 'EASY' | 'NORMAL' | 'HARD'
  companyName?: string | null
  position?: string | null
  region?: string | null
  experience?: string | null
  industry?: string | null
  memo?: string | null
  url?: string
}

export interface InterviewQuestionResponse {
  questions: string[]
  usedResume?: boolean
}

export function getInterviewQuestions(input: InterviewQuestionRequest): Promise<InterviewQuestionResponse> {
  return request('/ai/interview/questions', { method: 'POST', body: JSON.stringify(input) })
}

export interface ProfileResponse {
  profileText: string | null
}

export function getProfile(): Promise<ProfileResponse> {
  return request('/auth/me/profile')
}

export function saveProfile(profileText: string): Promise<ProfileResponse> {
  return request('/auth/me/profile', { method: 'PUT', body: JSON.stringify({ profileText }) })
}

export interface ProfileFileResponse {
  fileName: string | null
  fileType: string | null
  text?: string | null
}

export function saveProfileFile(file: File): Promise<ProfileFileResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request('/auth/me/profile/file', { method: 'PUT', body: formData })
}

export async function getProfileFile(): Promise<ProfileFileResponse | null> {
  const token = localStorage.getItem('accessToken')
  const res = await fetch(`${BASE_URL}/auth/me/profile/file`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 404) return null // 파일 없음 = 정상
  if (!res.ok) {
    throw new Error('이력서 파일을 불러오지 못했습니다.')
  }
  return res.json()
}

export function deleteProfileFile(): Promise<void> {
  return request('/auth/me/profile/file', { method: 'DELETE' })
}
