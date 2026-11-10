
import { useEffect, useState } from 'react'
import App from './App.jsx'
import Login from './components/Login/Login.jsx'
import { getCurrentUser } from './services/api/authApi.js'

const TOKEN_KEY = 'driveguard_access_token'
const USER_KEY = 'driveguard_user'

function normalizeRole(role) {
  return String(role ?? '')
    .trim()
    .toUpperCase()
    .replace(/^ROLE_/, '')
}

function AccessDenied({ user, onLogout }) {
  return (
    <main
      style={{
        minHeight: '100vh',
        background: '#0b1220',
        color: '#e5e7eb',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
      }}
    >
      <section
        style={{
          maxWidth: 520,
          padding: 32,
          background: '#131e30',
          border: '1px solid #293a52',
          borderRadius: 16,
        }}
      >
        <h1>DriveGuard | Chưa được cấp quyền</h1>
        <p>
          Tài khoản: {user?.fullName || user?.username}
        </p>
        <p>
          Vai trò: {normalizeRole(user?.role) || 'Chưa xác định'}
        </p>
        <p>
          Dashboard giám sát hiện chỉ hỗ trợ ADMIN.
          Các vai trò khác sẽ được mở khi Backend
          hoàn thiện phân quyền và phạm vi thiết bị.
        </p>
        <button type="button" onClick={onLogout}>
          Đăng xuất
        </button>
      </section>
    </main>
  )
}

function AppRouter() {
  const [authData, setAuthData] = useState(null)
  const [checkingSession, setCheckingSession] = useState(true)

  useEffect(() => {
    let active = true

    async function checkSession() {
      const token = sessionStorage.getItem(TOKEN_KEY)

      if (!token) {
        sessionStorage.removeItem(USER_KEY)
        if (active) {
          setAuthData(null)
          setCheckingSession(false)
        }
        return
      }

      try {
        const user = await getCurrentUser(token)

        if (!active) return

        sessionStorage.setItem(USER_KEY, JSON.stringify(user))
        setAuthData({ accessToken: token, user })
      } catch (error) {
        console.error('Lỗi xác thực phiên:', error)

        sessionStorage.removeItem(TOKEN_KEY)
        sessionStorage.removeItem(USER_KEY)

        if (active) setAuthData(null)
      } finally {
        if (active) setCheckingSession(false)
      }
    }

    checkSession()

    return () => {
      active = false
    }
  }, [])

  function handleLogin(data) {
    if (!data?.accessToken || !data?.user) return

    sessionStorage.setItem(TOKEN_KEY, data.accessToken)
    sessionStorage.setItem(USER_KEY, JSON.stringify(data.user))

    setAuthData({
      accessToken: data.accessToken,
      user: data.user,
    })
  }

  function handleLogout() {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    setAuthData(null)
  }

  if (checkingSession) {
    return (
      <main style={{
        minHeight: '100vh',
        background: '#0b1220',
        color: 'white',
        padding: 24,
      }}>
        Đang xác thực phiên đăng nhập...
      </main>
    )
  }

  if (!authData) {
    return <Login onLogin={handleLogin} />
  }

  const role = normalizeRole(authData.user?.role)

  if (role !== 'ADMIN') {
    return (
      <AccessDenied
        user={authData.user}
        onLogout={handleLogout}
      />
    )
  }

  return (
    <App
      user={authData.user}
      onLogout={handleLogout}
    />
  )
}

export default AppRouter
