
import { useState } from 'react'
import './login.css'
import { loginApi } from '../../services/api/authApi.js'

function Login({ onLogin }) {
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [showPassword, setShowPassword] = useState(false)
    const [error, setError] = useState('')



    const handleSubmit = async (event) => {
      event.preventDefault()
      setError('')

      try {
        const data = await loginApi(username.trim(), password)

        onLogin(data)
      } catch (err) {
        setError(err.message || 'Không thể kết nối máy chủ.')
      }
    }



  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <div className="login-logo">D</div>
          <div>
            <h1>DriveGuard</h1>
            <p>INTELLIGENT DRIVER MONITORING</p>
          </div>
        </div>

        <div className="login-heading">
          <span className="login-eyebrow">WELCOME BACK</span>
          <h2>Đăng nhập hệ thống</h2>
          <p>Đăng nhập để truy cập trung tâm giám sát tài xế.</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="login-username">Tên đăng nhập</label>
          <input
            id="login-username"
            type="text"
            autoComplete="username"
            placeholder="Nhập tên đăng nhập"
            value={username}
            onChange={(event) => {
              setUsername(event.target.value)
              setError('')
            }}
            required
          />

          <label htmlFor="login-password">Mật khẩu</label>
          <div className="login-password-wrapper">
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Nhập mật khẩu"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value)
                setError('')
              }}
              required
            />

            <button
            className="login-password-toggle"
            type="button"
            onClick={() => setShowPassword((current) => !current)}
            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
            {showPassword ? (
                <svg
                xmlns="http://www.w3.org/2000/svg"
                width="21"
                height="21"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                >
                <path d="M3 3l18 18" />
                <path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" />
                <path d="M9.9 5.2A10.8 10.8 0 0 1 12 5c5.2 0 8.7 4.1 10 7-0.5 1.1-1.3 2.3-2.4 3.3" />
                <path d="M6.2 6.2C3.9 7.6 2.5 9.7 2 12c1.3 2.9 4.8 7 10 7 1.1 0 2.1-.2 3-.6" />
                </svg>
            ) : (
                <svg
                xmlns="http://www.w3.org/2000/svg"
                width="21"
                height="21"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                >
                <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
                <circle cx="12" cy="12" r="3" />
                </svg>
            )}
            </button>

          </div>

          {error && (
            <div className="login-error" role="alert">
              {error}
            </div>
          )}

          <button className="login-submit" type="submit">
            Đăng nhập
            <span aria-hidden="true">→</span>
          </button>
        </form>

        <div className="login-security-note">
          <span aria-hidden="true">◈</span>
          Khu vực truy cập dành cho người dùng được cấp quyền
        </div>
      </section>

      <footer className="login-footer">
        DriveGuard · Hệ thống IoT giám sát tài xế
      </footer>
    </main>
  )
}

export default Login
