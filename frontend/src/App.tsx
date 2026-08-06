import { useState } from 'react'
import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import JobListPage from './pages/JobListPage'
import './App.css'

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(!!localStorage.getItem('accessToken'))

  return (
    <Routes>
      <Route
        path="/"
        element={
          isLoggedIn ? (
            <JobListPage onLogout={() => setIsLoggedIn(false)} />
          ) : (
            <LoginPage onLogin={() => setIsLoggedIn(true)} />
          )
        }
      />
    </Routes>
  )
}

export default App
