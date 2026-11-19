
const isValidCoordinate = (latitude, longitude) =>
  typeof latitude === 'number' &&
  typeof longitude === 'number' &&
  Number.isFinite(latitude) &&
  Number.isFinite(longitude) &&
  latitude >= -90 &&
  latitude <= 90 &&
  longitude >= -180 &&
  longitude <= 180

// Backend lưu UTC bằng LocalDateTime nên API có thể thiếu Z.
export function parseGpsUtc(value) {
  if (typeof value !== 'string' || !value.trim()) return NaN

  const normalized = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value)
    ? value
    : `${value}Z`

  return Date.parse(normalized)
}

export function formatGpsTime(value) {
  const timestamp = parseGpsUtc(value)

  if (!Number.isFinite(timestamp)) return '—'

  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp))
}

// Chuẩn hóa một bản ghi GPS.
export function normalizeGpsLocation(data) {
  if (!data || typeof data !== 'object') return null

  const { latitude, longitude } = data

  if (!isValidCoordinate(latitude, longitude)) return null

  const validNumber = (value, min, max = Infinity) =>
    typeof value === 'number' &&
    Number.isFinite(value) &&
    value >= min &&
    value <= max
      ? value
      : null

  return {
    id: data.id ?? null,
    deviceId: data.deviceId ?? null,
    messageId: data.messageId ?? null,
    latitude,
    longitude,
    accuracyM: validNumber(data.accuracyM, 0),
    speedKmh: validNumber(data.speedKmh, 0),
    headingDeg: validNumber(data.headingDeg, 0, 359.999),
    measuredAt: data.measuredAt ?? null,
    receivedAt: data.receivedAt ?? null,
  }
}

// Chuẩn hóa lịch sử và sắp xếp theo thời gian tăng dần.
export function normalizeGpsHistory(page) {
  if (!page || !Array.isArray(page.content)) {
    throw new Error('Dữ liệu lịch sử GPS không hợp lệ')
  }

  return page.content
    .map(normalizeGpsLocation)
    .filter((item) =>
      item !== null &&
      Number.isFinite(parseGpsUtc(item.measuredAt)),
    )
    .sort((a, b) =>
      parseGpsUtc(a.measuredAt) - parseGpsUtc(b.measuredAt) ||
      (a.id ?? 0) - (b.id ?? 0),
    )
}
