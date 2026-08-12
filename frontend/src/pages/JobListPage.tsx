import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { createPortal } from 'react-dom'
import Header from '../components/Header'
import StatusBadge from '../components/StatusBadge'
import JobFormModal from '../components/JobFormModal'
import KeywordsModal from '../components/KeywordsModal'
import ConfirmModal from '../components/ConfirmModal'
import InterviewSetupModal from '../components/InterviewSetupModal'
import ProfileModal from '../components/ProfileModal'
import { getJobs, getStats, updateJob, deleteJob, crawlCollected, getCollectedJobs, scrapCollectedJob, me, getProfile, getProfileFiles, type CollectedJobSearchField, type ProfileFileResponse } from '../api'
import { ALL_STATUSES, STATUS_LABEL, type ApplicationStatus, type JobPosting, type JobStats, type CollectedJob } from '../types'

// 상태별 통계 카드 이모지
const STATUS_ICON: Record<ApplicationStatus, string> = {
  WISH: '📋',
  APPLIED: '✉️',
  INTERVIEW: '🎤',
  OFFER: '🎉',
  REJECTED: '❌',
}

const SOURCES = ['잡코리아', '원티드']

// 출처별 뱃지 색상 클래스
const SOURCE_CLASS: Record<string, string> = {
  잡코리아: 'badge-applied',
  원티드: 'badge-interview',
}

export default function JobListPage({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<'mine' | 'collected'>('collected')
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [stats, setStats] = useState<JobStats>({})
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingJob, setEditingJob] = useState<JobPosting | null>(null)
  const [isInterviewModalOpen, setIsInterviewModalOpen] = useState(false)
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false)
  const [profileText, setProfileText] = useState<string | null>(null)
  const [resumeFiles, setResumeFiles] = useState<ProfileFileResponse[]>([])
  const [error, setError] = useState('')
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [visibleJobsCount, setVisibleJobsCount] = useState(30)

  const [collectedJobs, setCollectedJobs] = useState<CollectedJob[]>([])
  const [isCollectedLoading, setIsCollectedLoading] = useState(false)
  const [collectedKeyword, setCollectedKeyword] = useState('')
  const [searchField, setSearchField] = useState<CollectedJobSearchField>('all')
  const [sourceFilter, setSourceFilter] = useState<string>('ALL')
  const [scrapedIds, setScrapedIds] = useState<Set<number>>(new Set())
  const [collectedError, setCollectedError] = useState('')
  const [collectedMessage, setCollectedMessage] = useState('')
  const [visibleCollectedCount, setVisibleCollectedCount] = useState(30)

  const [keywords, setKeywords] = useState<string[]>([])
  const [isKeywordsModalOpen, setIsKeywordsModalOpen] = useState(false)
  const [keywordsMessage, setKeywordsMessage] = useState('')

  // 이력서 캐시 — 페이지 진입 시 1회만 조회 (모달 열 때마다 재조회하지 않음)
  useEffect(() => {
    getProfile()
      .then((res) => setProfileText(res.profileText ?? ''))
      .catch(() => setProfileText(null))
    getProfileFiles()
      .then(setResumeFiles)
      .catch(() => setResumeFiles([]))
  }, [])

  // 메시지는 3초 후 자동으로 사라진다 (새 메시지가 오면 이전 타이머는 취소)
  useEffect(() => {
    if (!error) return
    const timer = setTimeout(() => setError(''), 3000)
    return () => clearTimeout(timer)
  }, [error])

  useEffect(() => {
    if (!collectedError) return
    const timer = setTimeout(() => setCollectedError(''), 3000)
    return () => clearTimeout(timer)
  }, [collectedError])

  useEffect(() => {
    if (!collectedMessage) return
    const timer = setTimeout(() => setCollectedMessage(''), 3000)
    return () => clearTimeout(timer)
  }, [collectedMessage])

  useEffect(() => {
    if (!keywordsMessage) return
    const timer = setTimeout(() => setKeywordsMessage(''), 3000)
    return () => clearTimeout(timer)
  }, [keywordsMessage])

  const loadJobs = useCallback(async () => {
    setIsLoading(true)
    try {
      const data = await getJobs({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        keyword: keyword || undefined,
      })
      setJobs(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : '공고 목록을 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [statusFilter, keyword])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

  useEffect(() => {
    setVisibleJobsCount(30)
  }, [jobs])

  useEffect(() => {
    getStats()
      .then(setStats)
      .catch(() => {
        // 통계는 부가 정보이므로 실패해도 화면을 막지 않는다
      })
  }, [])

  useEffect(() => {
    me()
      .then((user) => setKeywords(user.keywords ?? []))
      .catch(() => {
        // 관심 분야는 부가 정보이므로 실패해도 화면을 막지 않는다
      })
  }, [])

  const refresh = () => {
    loadJobs()
    getStats()
      .then(setStats)
      .catch(() => {})
  }

  const handleSaved = () => {
    setIsModalOpen(false)
    setEditingJob(null)
    refresh()
  }

  const handleStatusChange = async (job: JobPosting, status: ApplicationStatus) => {
    await updateJob(job.id, { status })
    refresh()
  }

  const handleDelete = (id: number) => {
    setDeleteTargetId(id)
  }

  const confirmDelete = async () => {
    if (deleteTargetId === null) return
    await deleteJob(deleteTargetId)
    setDeleteTargetId(null)
    refresh()
  }

  const openCreateModal = () => {
    setEditingJob(null)
    setIsModalOpen(true)
  }

  const openEditModal = (job: JobPosting) => {
    setEditingJob(job)
    setIsModalOpen(true)
  }

  // 첫 로드 시 동시 요청 경합으로 500이 간헐 발생 → 1회 자동 재시도로 방어
  const collectedRetryRef = useRef(0)

  const loadCollected = useCallback(async () => {
    setIsCollectedLoading(true)
    try {
      const data = await getCollectedJobs({})
      // 스크랩한 공고는 이미 확인했으므로 목록 아래로 내린다 (createdAt DESC는 그대로 유지)
      const sorted = [...data].sort((a, b) => Number(a.scrapedByMe) - Number(b.scrapedByMe))
      setCollectedJobs(sorted)
      setScrapedIds(new Set(data.filter((j) => j.scrapedByMe).map((j) => j.id)))
      setCollectedError('')
      collectedRetryRef.current = 0
    } catch (err) {
      if (collectedRetryRef.current === 0) {
        collectedRetryRef.current++
        setTimeout(() => loadCollected(), 800)
        return
      }
      collectedRetryRef.current = 0
      setCollectedError(err instanceof Error ? err.message : '수집 공고를 불러오지 못했습니다.')
    } finally {
      setIsCollectedLoading(false)
    }
  }, [])

  useEffect(() => {
    if (tab === 'collected') loadCollected()
  }, [tab, loadCollected])

  // 검색/출처/내 공고 필터는 서버 재조회 없이 이미 받아둔 목록에서 즉시 걸러낸다
  const filteredCollected = useMemo(() => {
    let list = collectedJobs
    const kw = collectedKeyword.trim().toLowerCase()
    if (kw) {
      list = list.filter((j) => {
        const title = (j.title ?? '').toLowerCase()
        const company = (j.company ?? '').toLowerCase()
        if (searchField === 'company') return company.includes(kw)
        if (searchField === 'title') return title.includes(kw)
        return title.includes(kw) || company.includes(kw)
      })
    }
    if (sourceFilter !== 'ALL') list = list.filter((j) => j.source === sourceFilter)
    return list
  }, [collectedJobs, collectedKeyword, searchField, sourceFilter])

  useEffect(() => {
    setVisibleCollectedCount(30)
  }, [filteredCollected])

  const handleLoadCollected = async () => {
    setCollectedError('')
    setCollectedMessage('')
    try {
      const result = await crawlCollected(keywords)
      setCollectedMessage(result.loaded > 0 ? `${result.loaded}건 새로 가져왔어요` : '새 공고가 없어요')
      loadCollected()
    } catch (err) {
      setCollectedError(err instanceof Error ? err.message : '공고를 불러오지 못했습니다.')
    }
  }

  const handleKeywordsSaved = (newKeywords: string[], message?: string, keepOpen?: boolean) => {
    setKeywords(newKeywords)
    if (!keepOpen) setIsKeywordsModalOpen(false)
    setKeywordsMessage(message ?? '관심 분야를 저장했어요')
    if (tab === 'collected' && message) loadCollected()
  }

  const handleScrap = async (id: number) => {
    setCollectedError('')
    setCollectedMessage('')
    try {
      await scrapCollectedJob(id)
      setScrapedIds((prev) => new Set(prev).add(id))
      setCollectedJobs((prev) => {
        const updated = prev.map((j) => (j.id === id ? { ...j, scrapedByMe: true } : j))
        return [...updated].sort((a, b) => Number(a.scrapedByMe) - Number(b.scrapedByMe))
      })
      setCollectedMessage('내 공고로 가져왔어요')
    } catch (err) {
      setCollectedError(err instanceof Error ? err.message : '스크랩에 실패했습니다.')
    }
  }

  return (
    <div className="job-list-page">
      <Header
        onLogout={onLogout}
        onOpenKeywords={() => setIsKeywordsModalOpen(true)}
        onOpenInterview={() => setIsInterviewModalOpen(true)}
        onOpenProfile={() => setIsProfileModalOpen(true)}
      />
      <main className="content">
        {keywordsMessage &&
          createPortal(<div className="toast toast-success">{keywordsMessage}</div>, document.body)}
        <div className="tabs">
          <button
            type="button"
            className={tab === 'collected' ? 'tab active' : 'tab'}
            onClick={() => setTab('collected')}
          >
            전체 공고
          </button>
          <button
            type="button"
            className={tab === 'mine' ? 'tab active' : 'tab'}
            onClick={() => {
              setTab('mine')
              refresh() // 탭 전환 시마다 최신 목록·통계를 다시 불러온다 (스크랩 반영)
            }}
          >
            내 공고
          </button>
        </div>

        {tab === 'mine' && (
          <>
            <section className="stats">
              {ALL_STATUSES.map((status) => (
                <div key={status} className="stat-card">
                  <span className="stat-icon">{STATUS_ICON[status]}</span>
                  <span className="stat-label">{STATUS_LABEL[status]}</span>
                  <span className="stat-count">{stats[status] ?? 0}</span>
                </div>
              ))}
            </section>

            <section className="toolbar">
              <div className="search-wrapper">
                <input
                  type="text"
                  className="search-input"
                  placeholder="회사명 또는 포지션 검색"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                />
              </div>
              <div className="status-filters">
                <button
                  type="button"
                  className={statusFilter === 'ALL' ? 'chip active' : 'chip'}
                  onClick={() => setStatusFilter('ALL')}
                >
                  전체
                </button>
                {ALL_STATUSES.map((status) => (
                  <button
                    key={status}
                    type="button"
                    className={statusFilter === status ? 'chip active' : 'chip'}
                    onClick={() => setStatusFilter(status)}
                  >
                    {STATUS_LABEL[status]}
                  </button>
                ))}
              </div>
              <button type="button" className="primary-button" onClick={openCreateModal}>
                공고 추가
              </button>
            </section>

            {error &&
              createPortal(<div className="toast toast-error">{error}</div>, document.body)}

            <section className="job-list">
              {isLoading && jobs.length === 0 ? (
                <div className="job-list-loading">공고를 불러오는 중...</div>
              ) : jobs.length === 0 ? (
                <p className="empty-message">아직 등록한 공고가 없어요 — [+ 공고 추가] 버튼으로 시작해보세요</p>
              ) : null}
              {jobs.slice(0, visibleJobsCount).map((job) => (
                <article key={job.id} className="job-card">
                  <div className="job-card-header">
                    <h3 title={job.companyName}>{job.companyName}</h3>
                    <StatusBadge status={job.status} />
                  </div>
                  <p className="job-position" title={job.position}>{job.position}</p>
                  {(job.region || job.experience || job.industry) && (
                    <p className="job-meta">
                      {[job.region, job.experience, job.industry].filter(Boolean).join(' · ')}
                    </p>
                  )}
                  {job.deadline && <p className="job-deadline">마감일: {job.deadline}</p>}
                  {job.memo && <p className="job-memo">{job.memo}</p>}
                  <div className="job-card-footer">
                    <div className="job-card-actions">
                      <select
                        value={job.status}
                        onChange={(e) => handleStatusChange(job, e.target.value as ApplicationStatus)}
                      >
                        {ALL_STATUSES.map((status) => (
                          <option key={status} value={status}>
                            {STATUS_LABEL[status]}
                          </option>
                        ))}
                      </select>
                      <button type="button" className="outline-button" onClick={() => openEditModal(job)}>
                        수정
                      </button>
                      <button type="button" className="danger-button" onClick={() => handleDelete(job.id)}>
                        삭제
                      </button>
                    </div>
                  </div>
                </article>
              ))}
              {visibleJobsCount < jobs.length && (
                <button
                  type="button"
                  className="load-more-button"
                  onClick={() => setVisibleJobsCount((c) => c + 30)}
                >
                  더보기 ({visibleJobsCount} / {jobs.length})
                </button>
              )}
            </section>
          </>
        )}

        {tab === 'collected' && (
          <>
            <section className="toolbar">
              <select
                className="search-field-select"
                aria-label="검색 범위"
                value={searchField}
                onChange={(e) => setSearchField(e.target.value as CollectedJobSearchField)}
              >
                <option value="all">전체</option>
                <option value="company">회사명</option>
                <option value="title">제목</option>
              </select>
              <div className="search-wrapper">
                <input
                  type="text"
                  className="search-input"
                  placeholder={
                    searchField === 'company'
                      ? '회사명 검색'
                      : searchField === 'title'
                        ? '제목 검색'
                        : '회사명 또는 포지션 검색'
                  }
                  value={collectedKeyword}
                  onChange={(e) => setCollectedKeyword(e.target.value)}
                />
              </div>
              <div className="status-filters">
                <button
                  type="button"
                  className={sourceFilter === 'ALL' ? 'chip active' : 'chip'}
                  onClick={() => setSourceFilter('ALL')}
                >
                  전체
                </button>
                {SOURCES.map((source) => (
                  <button
                    key={source}
                    type="button"
                    className={sourceFilter === source ? 'chip active' : 'chip'}
                    onClick={() => setSourceFilter(source)}
                  >
                    {source}
                  </button>
                ))}
              </div>
              <span className="sort-label">정렬: 최신 수집순</span>
              <button type="button" className="primary-button" onClick={handleLoadCollected}>
                공고 불러오기
              </button>
            </section>

            {collectedError &&
              createPortal(<div className="toast toast-error">{collectedError}</div>, document.body)}
            {collectedMessage &&
              createPortal(<div className="toast toast-success">{collectedMessage}</div>, document.body)}

            <section className="job-list">
              {isCollectedLoading && collectedJobs.length === 0 ? (
                <div className="job-list-loading">공고를 불러오는 중...</div>
              ) : filteredCollected.length === 0 ? (
                <p className="empty-message">아직 불러온 공고가 없어요 — [공고 불러오기]를 눌러 크롤링해보세요</p>
              ) : null}
              {filteredCollected.slice(0, visibleCollectedCount).map((job) => {
                const scraped = scrapedIds.has(job.id)
                return (
                  <article key={job.id} className={scraped ? 'job-card job-card-scraped' : 'job-card'}>
                    <div className="job-card-header">
                      <h3 title={job.company}>{job.company}</h3>
                      <span className={`badge ${SOURCE_CLASS[job.source] ?? 'badge-wish'}`}>{job.source}</span>
                    </div>
                    <p className="job-position" title={job.title}>{job.title}</p>
                    {(job.region || job.experience || job.industry || job.deadline) && (
                      <p className="job-meta">
                        {[
                          job.region,
                          job.experience,
                          job.industry,
                          job.deadline &&
                            `마감 ${job.deadline
                              .split('-')
                              .slice(1)
                              .map(Number)
                              .join('/')}`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </p>
                    )}
                    <div className="job-card-footer">
                      <a href={job.url} target="_blank" rel="noreferrer" className="job-link" title={job.url}>
                        {job.url}
                      </a>
                      <div className="job-card-actions">
                        <button
                          type="button"
                          className="primary-button"
                          disabled={scraped}
                          onClick={() => handleScrap(job.id)}
                        >
                          {scraped ? '✓ 스크랩 완료' : '스크랩'}
                        </button>
                      </div>
                    </div>
                  </article>
                )
              })}
              {visibleCollectedCount < filteredCollected.length && (
                <button
                  type="button"
                  className="load-more-button"
                  onClick={() => setVisibleCollectedCount((c) => c + 30)}
                >
                  더보기 ({visibleCollectedCount} / {filteredCollected.length})
                </button>
              )}
            </section>
          </>
        )}
      </main>

      {isModalOpen && (
        <JobFormModal
          job={editingJob}
          onClose={() => {
            setIsModalOpen(false)
            setEditingJob(null)
          }}
          onSaved={handleSaved}
        />
      )}

      {isKeywordsModalOpen && (
        <KeywordsModal
          currentKeywords={keywords}
          onClose={() => setIsKeywordsModalOpen(false)}
          onSaved={handleKeywordsSaved}
        />
      )}

      <ConfirmModal
        open={deleteTargetId !== null}
        message="이 공고를 삭제하시겠습니까?"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTargetId(null)}
      />

      <InterviewSetupModal
        open={isInterviewModalOpen}
        onClose={() => setIsInterviewModalOpen(false)}
        jobs={jobs}
        profileText={profileText}
        hasResumeFile={resumeFiles.length > 0}
      />

      {isProfileModalOpen && (
        <ProfileModal
          open={isProfileModalOpen}
          onClose={() => setIsProfileModalOpen(false)}
          initialProfileText={profileText ?? ''}
          initialFiles={resumeFiles}
          onProfileSaved={setProfileText}
          onFilesChanged={setResumeFiles}
        />
      )}
    </div>
  )
}
