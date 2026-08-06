export default function Header({ onLogout }: { onLogout: () => void }) {
  const handleLogout = () => {
    localStorage.removeItem('accessToken')
    onLogout()
  }

  return (
    <header className="header">
      <h1>Job Tracker</h1>
      <button type="button" onClick={handleLogout}>
        로그아웃
      </button>
    </header>
  )
}
