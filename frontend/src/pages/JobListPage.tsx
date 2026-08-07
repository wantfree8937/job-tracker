import { useState, useEffect, useCallback } from 'react'
import Header from '../components/Header'
import StatusBadge from '../components/StatusBadge'
import JobFormModal from '../components/JobFormModal'
import { getJobs, getStats, updateJob, deleteJob } from '../api'
import { ALL_STATUSES, STATUS_LABEL, type ApplicationStatus, type JobPosting, type JobStats } from '../types'

// 상태별 통계 카드 이모지
const STATUS_ICON: Record<ApplicationStatus, string> = {
  WISH: '📋',
  APPLIED: '✉️',
  INTERVIEW: '🎤',
  OFFER: '🎉',
  REJECTED: '❌',
}

export default function JobListPage({ onLogout }: { onLogout: () => void }) {
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [stats, setStats] = useState<JobStats>({})
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingJob, setEditingJob] = useState<JobPosting | null>(null)
  const [error, setError] = useState('')

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

  const handleDelete = async (id: number) => {
    if (!window.confirm('이 공고를 삭제하시겠습니까?')) return
    await deleteJob(id)
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

  return (
    <div className="job-list-page">
      <Header onLogout={onLogout} />
      <main className="content">
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

        {error && <p className="error-message">{error}</p>}

        <section className="job-list">
          {jobs.length === 0 && <p className="empty-message">등록된 공고가 없습니다.</p>}
          {jobs.map((job) => (
            <article key={job.id} className="job-card">
              <div className="job-card-header">
                <h3>{job.companyName}</h3>
                <StatusBadge status={job.status} />
              </div>
              <p className="job-position">{job.position}</p>
              {job.deadline && <p className="job-deadline">마감일: {job.deadline}</p>}
              {job.memo && <p className="job-memo">{job.memo}</p>}
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
            </article>
          ))}
        </section>
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
    </div>
  )
}
