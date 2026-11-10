
import { apiGet } from './apiClient.js'

const BASE_PATH = '/api/v1/sensor-readings'

function validateDeviceId(deviceId) {
  const id = Number(deviceId)

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('deviceId phải là số nguyên dương')
  }

  return id
}

export async function getLatestSensorReading(
  deviceId,
  options = {}
) {
  const id = validateDeviceId(deviceId)

  return apiGet(
    `${BASE_PATH}/devices/${id}/latest`,
    options
  )
}

export async function getSensorReadingHistory(
  deviceId,
  { page = 0, size = 100, signal } = {}
) {
  const id = validateDeviceId(deviceId)

  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  return apiGet(
    `${BASE_PATH}/devices/${id}?${params}`,
    { signal }
  )
}
