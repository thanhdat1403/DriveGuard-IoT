
import { apiGet } from './apiClient.js'

const BASE_PATH = '/api/v1/alerts'

export async function getAlerts({
  deviceId = 1,
  sosOnly = false,
  page = 0,
  size = 20,
  signal,
} = {}) {
  const id = Number(deviceId)

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('deviceId không hợp lệ')
  }

  const params = new URLSearchParams({
    deviceId: String(id),
    sosOnly: String(sosOnly),
    page: String(page),
    size: String(size),
  })

  return apiGet(
    `${BASE_PATH}?${params}`,
    { signal }
  )
}

export function getSosAlerts(options = {}) {
  return getAlerts({
    ...options,
    sosOnly: true,
  })
}
