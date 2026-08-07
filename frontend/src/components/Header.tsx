export default function Header({
  onLogout,
  onOpenKeywords,
}: {
  onLogout: () => void
  onOpenKeywords: () => void
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
        <button type="button" onClick={handleLogout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}
