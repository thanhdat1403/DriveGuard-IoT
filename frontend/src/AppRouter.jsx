
import { useEffect, useState } from 'react'
import App from './App.jsx'
import Login from './components/Login/Login.jsx'
import { getCurrentUser } from './services/api/authApi.js'

const TOKEN_KEY = 'driveguard_access_token'
const USER_KEY = 'driveguard_user'

function AppRouter() {
  const [authData, setAuthData] = useState(null)
  const [checkingSession, setCheckingSession] = useState(true)

  useEffect(() => {
    let isMounted = true

    const checkSession = async () => {
      const token = sessionStorage.getItem(TOKEN_KEY)

      if (!token) {
        if (isMounted) setCheckingSession(false)
        return
      }

      try {

      const cachedUser = JSON.parse(
        sessionStorage.getItem(USER_KEY) || '{}'
      )

      const currentUser = await getCurrentUser(token)

      if (isMounted) {
        setAuthData({
          accessToken: token,
          user: {
            ...cachedUser,
            ...currentUser,
          },
        })
      }

      } catch {
        sessionStorage.removeItem(TOKEN_KEY)

        if (isMounted) {
          setAuthData(null)
        }
      } finally {
        if (isMounted) {
          setCheckingSession(false)
        }
      }
    }

    checkSession()

    return () => {
      isMounted = false
    }
  }, [])

  const handleLogin = (data) => {
    sessionStorage.setItem(TOKEN_KEY, data.accessToken)
    sessionStorage.setItem(USER_KEY, JSON.stringify(data.user))
    setAuthData(data)
  }

  const handleLogout = () => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    setAuthData(null)
  }

  if (checkingSession) {
    return (
      <div style={{ padding: 24, color: '#fff', background: '#0b1220', minHeight: '100vh' }}>
        Đang kiểm tra phiên đăng nhập...
      </div>
    )
  }

  if (!authData) {
    return <Login onLogin={handleLogin} />
  }

  return <App onLogout={handleLogout} user={authData.user} />
}

export default AppRouter
