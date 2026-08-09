import { useState, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import Header from '../components/Header'
import StatusBadge from '../components/StatusBadge'
import JobFormModal from '../components/JobFormModal'
import KeywordsModal from '../components/KeywordsModal'
import ConfirmModal from '../components/ConfirmModal'
import { getJobs, getStats, updateJob, deleteJob, loadCollectedJobs, getCollectedJobs, scrapCollectedJob, me, type CollectedJobSearchField } from '../api'
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
  const [error, setError] = useState('')
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null)

  const [collectedJobs, setCollectedJobs] = useState<CollectedJob[]>([])
  const [collectedKeyword, setCollectedKeyword] = useState('')
  const [searchField, setSearchField] = useState<CollectedJobSearchField>('all')
  const [sourceFilter, setSourceFilter] = useState<string>('ALL')
  const [mineOnly, setMineOnly] = useState(false)
  const [scrapedIds, setScrapedIds] = useState<Set<number>>(new Set())
  const [collectedError, setCollectedError] = useState('')
  const [collectedMessage, setCollectedMessage] = useState('')

  const [keywords, setKeywords] = useState<string[]>([])
  const [isKeywordsModalOpen, setIsKeywordsModalOpen] = useState(false)
  const [keywordsMessage, setKeywordsMessage] = useState('')

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
    try {
      const data = await getJobs({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        keyword: keyword || undefined,
      })
      setJobs(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : '공고 목록을 불러오지 못했습니다.')
    }
  }, [statusFilter, keyword])

  useEffect(() => {
    loadJobs()
  }, [loadJobs])

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

  const loadCollected = useCallback(async () => {
    try {
      const data = await getCollectedJobs({
        keyword: collectedKeyword || undefined,
        source: sourceFilter === 'ALL' ? undefined : sourceFilter,
        mine: mineOnly || undefined,
        searchField,
      })
      // 스크랩한 공고는 이미 확인했으므로 목록 아래로 내린다 (createdAt DESC는 그대로 유지)
      const sorted = [...data].sort((a, b) => Number(a.scrapedByMe) - Number(b.scrapedByMe))
      setCollectedJobs(sorted)
      setScrapedIds(new Set(data.filter((j) => j.scrapedByMe).map((j) => j.id)))
    } catch (err) {
      setCollectedError(err instanceof Error ? err.message : '수집 공고를 불러오지 못했습니다.')
    }
  }, [collectedKeyword, searchField, sourceFilter, mineOnly])

  useEffect(() => {
    if (tab === 'collected') loadCollected()
  }, [tab, loadCollected])

  const handleLoadCollected = async () => {
    setCollectedError('')
    setCollectedMessage('')
    try {
      const result = await loadCollectedJobs()
      setCollectedMessage(`${result.loaded}건 불러왔어요`)
      loadCollected()
    } catch (err) {
      setCollectedError(err instanceof Error ? err.message : '공고를 불러오지 못했습니다.')
    }
  }

  const handleKeywordsSaved = (newKeywords: string[], message?: string, keepOpen?: boolean) => {
    setKeywords(newKeywords)
    if (!keepOpen) setIsKeywordsModalOpen(false)
    setKeywordsMessage(message)
    if (tab === 'collected' && (mineOnly || message)) loadCollected()
  }

  const handleScrap = async (id: number) => {
    setCollectedError('')
    setCollectedMessage('')
    try {
      await scrapCollectedJob(id)
      setScrapedIds((prev) => new Set(prev).add(id))
      setCollectedMessage('내 공고로 가져왔어요')
    } catch (err) {
      setCollectedError(err instanceof Error ? err.message : '스크랩에 실패했습니다.')
    }
  }

  return (
    <div className="job-list-page">
      <Header onLogout={onLogout} onOpenKeywords={() => setIsKeywordsModalOpen(true)} />
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
              {jobs.length === 0 && <p className="empty-message">등록된 공고가 없습니다.</p>}
              {jobs.map((job) => (
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
            </section>
          </>
        )}

        {tab === 'collected' && (
          <>
            <div className="status-filters">
              <button
                type="button"
                className={!mineOnly ? 'chip active' : 'chip'}
                onClick={() => setMineOnly(false)}
              >
                전체 공고
              </button>
              <button
                type="button"
                className={mineOnly ? 'chip active' : 'chip'}
                onClick={() => setMineOnly(true)}
              >
                내 관심 공고
              </button>
            </div>

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
                  placeholder="회사명 또는 포지션 검색"
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
              {collectedJobs.length === 0 && (
                <p className="empty-message">공고를 불러와주세요 (공고 불러오기 버튼)</p>
              )}
              {collectedJobs.map((job) => {
                const scraped = scrapedIds.has(job.id)
                return (
                  <article key={job.id} className={scraped ? 'job-card job-card-scraped' : 'job-card'}>
                    <div className="job-card-header">
                      <h3 title={job.company}>{job.company}</h3>
                      <span className={`badge ${SOURCE_CLASS[job.source] ?? 'badge-wish'}`}>{job.source}</span>
                    </div>
                    <p className="job-position" title={job.title}>{job.title}</p>
                    {(job.region || job.experience || job.industry) && (
                      <p className="job-meta">
                        {[job.region, job.experience, job.industry].filter(Boolean).join(' · ')}
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
    </div>
  )
}
