export default function Header({
  onLogout,
  onOpenKeywords,
  onOpenInterview,
}: {
  onLogout: () => void
  onOpenKeywords: () => void
  onOpenInterview: () => void
}) {
  const handleLogout = () => {
    localStorage.removeItem('accessToken')
    onLogout()
  }

  return (
    <header className="header">
      <h1>Job Tracker</h1>
      <div className="header-actions">
        <button type="button" onClick={onOpenKeywords}>
          관심 분야
        </button>
        <button type="button" onClick={onOpenInterview}>
          AI 면접
        </button>
        <button type="button" onClick={handleLogout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}
