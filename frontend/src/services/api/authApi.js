
const API_BASE_URL = 'http://localhost:3000/api'

export async function loginApi(username, password) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  })

  const result = await response.json().catch(() => null)

  if (!response.ok || result?.success !== true) {
    throw new Error(
      result?.message || 'Đăng nhập thất bại. Vui lòng kiểm tra tài khoản.',
    )
  }

  const data = result.data

  if (!data?.accessToken || !data?.user) {
    throw new Error('Phản hồi đăng nhập từ máy chủ không hợp lệ.')
  }

  return data
}

export async function getCurrentUser(token) {
  const response = await fetch(`${API_BASE_URL}/auth/me`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  const result = await response.json().catch(() => null)

  if (!response.ok || result?.success !== true) {
    throw new Error(
      result?.message || 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.',
    )
  }

  return result.data
}
