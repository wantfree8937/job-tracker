import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { login, getJobs, signup, createJob, updateJob, deleteJob, previewLink, scrapCollectedJob } from './api'

function mockFetchOnce(status: number, body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  })
}

describe('api', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('로그인 성공 시 토큰을 localStorage에 저장한다', async () => {
    vi.stubGlobal(
      'fetch',
      mockFetchOnce(200, { accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 3600 }),
    )

    await login('test@example.com', 'password123')

    expect(localStorage.getItem('accessToken')).toBe('test-token')
  })

  it('401 응답을 받으면 저장된 토큰을 제거한다', async () => {
    localStorage.setItem('accessToken', 'expired-token')
    vi.stubGlobal('fetch', mockFetchOnce(401, { message: '인증이 만료되었습니다.' }))

    await expect(login('test@example.com', 'wrong-password')).rejects.toThrow()

    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('getJobs 호출 시 Authorization 헤더에 토큰을 포함한다', async () => {
    localStorage.setItem('accessToken', 'test-token')
    const fetchMock = mockFetchOnce(200, [])
    vi.stubGlobal('fetch', fetchMock)

    await getJobs()

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/jobs'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer test-token' }) }),
    )
  })

  it('회원가입 성공 시 생성된 사용자 정보를 반환한다', async () => {
    const user = { id: 1, email: 'test@example.com', nickname: 'tester', createdAt: '2026-08-07' }
    vi.stubGlobal('fetch', mockFetchOnce(200, user))

    await expect(signup('test@example.com', 'password123', 'tester')).resolves.toEqual(user)
  })

  it('서버가 에러 메시지를 내려주면 해당 메시지로 예외를 던진다', async () => {
    vi.stubGlobal('fetch', mockFetchOnce(400, { message: '이미 가입된 이메일입니다.' }))

    await expect(signup('dup@example.com', 'password123', 'tester')).rejects.toThrow(
      '이미 가입된 이메일입니다.',
    )
  })

  it('공고 생성/수정/삭제는 올바른 경로와 메서드로 요청한다', async () => {
    const fetchMock = mockFetchOnce(200, {})
    vi.stubGlobal('fetch', fetchMock)

    await createJob({ companyName: '회사', position: '백엔드' })
    expect(fetchMock).toHaveBeenLastCalledWith('http://localhost:8080/api/jobs', expect.objectContaining({ method: 'POST' }))

    await updateJob(1, { status: 'APPLIED' })
    expect(fetchMock).toHaveBeenLastCalledWith('http://localhost:8080/api/jobs/1', expect.objectContaining({ method: 'PATCH' }))

    await deleteJob(1)
    expect(fetchMock).toHaveBeenLastCalledWith('http://localhost:8080/api/jobs/1', expect.objectContaining({ method: 'DELETE' }))
  })

  it('previewLink는 링크를 담아 POST로 프리뷰를 요청한다', async () => {
    const fetchMock = mockFetchOnce(200, { company: '카카오', position: '백엔드', memo: null })
    vi.stubGlobal('fetch', fetchMock)

    await previewLink('https://example.com/job')

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/jobs/preview',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ link: 'https://example.com/job' }) }),
    )
  })

  it('scrapCollectedJob은 수집 공고 id로 POST 스크랩 요청을 보낸다', async () => {
    const fetchMock = mockFetchOnce(201, {})
    vi.stubGlobal('fetch', fetchMock)

    await scrapCollectedJob(5)

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/jobs/collected/5/scrap',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
