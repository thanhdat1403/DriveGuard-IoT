
import { apiGet } from './apiClient.js'

// Lấy vị trí GPS mới nhất.
export function getLatestDeviceLocation(deviceId, { signal } = {}) {
  if (!Number.isSafeInteger(deviceId) || deviceId <= 0) {
    throw new Error('deviceId không hợp lệ')
  }

  return apiGet(
    `/api/v1/devices/${deviceId}/locations/latest`,
    { signal },
  )
}

// Backend cho phép tối đa 100 bản ghi mỗi trang.
export function getDeviceLocationHistory(
  deviceId,
  { page = 0, size = 100, signal } = {},
) {
  if (!Number.isSafeInteger(deviceId) || deviceId <= 0) {
    throw new Error('deviceId không hợp lệ')
  }

  if (!Number.isSafeInteger(page) || page < 0) {
    throw new Error('page không hợp lệ')
  }

  if (!Number.isSafeInteger(size) || size < 1 || size > 100) {
    throw new Error('size phải từ 1 đến 100')
  }

  return apiGet(
    `/api/v1/devices/${deviceId}/locations?page=${page}&size=${size}`,
    { signal },
  )
}
