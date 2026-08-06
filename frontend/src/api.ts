import type { AuthResponse, JobPosting, ApplicationStatus, JobStats, User } from './types'

const BASE_URL = 'http://localhost:8080/api'

interface ApiErrorBody {
  message: string
}

// 공통 fetch 래퍼: 토큰 첨부, 401 처리, 에러 메시지 파싱
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('accessToken')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
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
