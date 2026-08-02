import { useState } from 'react'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(
    Boolean(sessionStorage.getItem('accessToken')),
  )

  function handleLoginSuccess() {
    setIsAuthenticated(true)
  }

  if (isAuthenticated) {
    return <DashboardPage />
  }

  return <LoginPage onLoginSuccess={handleLoginSuccess} />
}

export default App