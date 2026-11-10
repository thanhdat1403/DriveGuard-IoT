
const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL ||
  'http://localhost:8081'
).replace(/\/+$/, '')

const TOKEN_KEY = 'driveguard_access_token'

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function apiGet(path, { signal } = {}) {
  const token = sessionStorage.getItem(TOKEN_KEY)

  if (!token) {
    throw new ApiError('Phiên đăng nhập không tồn tại', 401)
  }

  let response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error

    throw new ApiError(
      'Không kết nối được Spring Boot. Kiểm tra cổng 8081.'
    )
  }

  if (response.status === 401) {
    throw new ApiError('Phiên đăng nhập hết hạn hoặc không hợp lệ', 401)
  }

  if (response.status === 403) {
    throw new ApiError('Tài khoản không có quyền truy cập dữ liệu', 403)
  }

  if (!response.ok) {
    throw new ApiError(
      `API trả về lỗi HTTP ${response.status}`,
      response.status
    )
  }

  const result = await response.json()

  if (result?.success === false) {
    throw new ApiError(
      result.message || 'Backend trả về lỗi nghiệp vụ',
      response.status
    )
  }

  return result?.data ?? result
}
