
const DEFAULT_STALE_AFTER_MS = 15000

function validNumber(value, { min = -Infinity, max = Infinity } = {}) {
  if (typeof value !== 'number') return null
  if (!Number.isFinite(value)) return null
  if (value < min || value > max) return null
  return value
}

function normalizeEnum(value) {
  if (typeof value !== 'string') return null

  const normalized = value.trim().toUpperCase()
  return normalized || null
}

// Spring Boot hiện trả LocalDateTime không có múi giờ.
// Tạm thời dùng UTC theo quy ước Backend.
// Cần xác minh cấu hình thời gian trước khi triển khai thực tế.
function parseBackendTime(value) {
  if (!value || typeof value !== 'string') return null

  const timestamp = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value)
    ? value
    : `${value}Z`

  const parsed = Date.parse(timestamp)
  return Number.isFinite(parsed) ? parsed : null
}

export function normalizeLiveReading(
  raw,
  { now = Date.now(), staleAfterMs = DEFAULT_STALE_AFTER_MS } = {}
) {
  if (!raw || typeof raw !== 'object') {
    return null
  }

  const measuredAtMs = parseBackendTime(raw.measuredAt)
  const receivedAtMs = parseBackendTime(raw.receivedAt)
  const lastUpdatedMs = receivedAtMs ?? measuredAtMs

  const ageMs =
    lastUpdatedMs === null ? null : now - lastUpdatedMs

  const isFresh =
    ageMs !== null &&
    ageMs >= -5000 &&
    ageMs <= staleAfterMs

  const heartRate = validNumber(raw.heartRate, {
    min: 1,
    max: 300,
  })

  const spo2 = validNumber(raw.spo2, {
    min: 1,
    max: 100,
  })

  const edgeAiStatus = normalizeEnum(raw.edgeAiStatus)
  const serverStatus = normalizeEnum(raw.serverStatus)
  const riskLevel = normalizeEnum(raw.riskLevel)

  return {
    id: raw.id ?? null,
    deviceId: raw.deviceId ?? null,
    deviceCode: raw.deviceCode ?? null,
    deviceName: raw.deviceName ?? null,

    measuredAt: raw.measuredAt ?? null,
    receivedAt: raw.receivedAt ?? null,
    measuredAtMs,
    receivedAtMs,
    ageMs,
    isFresh,

    heartRate,
    spo2,

    edgeAiStatus,
    edgeAiConfidence: validNumber(raw.edgeAiConfidence, {
      min: 0,
      max: 1,
    }),

    serverStatus,
    riskLevel,
    isDrowsy:
      typeof raw.isDrowsy === 'boolean'
        ? raw.isDrowsy
        : null,

    // Chưa có trong SensorReadingResponse của Backend.
    headTiltDegrees: null,
    eyeClosureDurationMs: null,
    eyeStatus: null,
    headStatus: null,

    source: 'SPRING_BOOT',
  }
}

export function getMonitoringStatus(reading) {
  if (!reading) {
    return {
      code: 'NO_DATA',
      label: 'Chưa có dữ liệu',
      tone: 'unknown',
    }
  }

  if (!reading.isFresh) {
    return {
      code: 'STALE',
      label: 'Dữ liệu quá hạn',
      tone: 'unknown',
    }
  }

  const highRisk =
    ['HIGH', 'CRITICAL', 'DANGER'].includes(reading.riskLevel) ||
    ['CRITICAL', 'DANGER'].includes(reading.serverStatus)

  if (highRisk) {
    return {
      code: 'CRITICAL',
      label: 'Nguy hiểm',
      tone: 'critical',
    }
  }

  if (
    reading.isDrowsy === true ||
    ['DROWSY', 'WARNING'].includes(reading.edgeAiStatus) ||
    ['WARNING', 'DROWSY'].includes(reading.serverStatus) ||
    reading.riskLevel === 'MEDIUM'
  ) {
    return {
      code: 'WARNING',
      label: 'Cảnh báo',
      tone: 'warning',
    }
  }

  if (
    reading.heartRate === null ||
    reading.spo2 === null ||
    reading.serverStatus === null ||
    reading.serverStatus === 'UNKNOWN' ||
    reading.riskLevel === null
  ) {
    return {
      code: 'INSUFFICIENT_DATA',
      label: 'Không đủ dữ liệu',
      tone: 'unknown',
    }
  }

  // Không tự suy luận "an toàn" chỉ từ BPM/SpO2.
  // Trạng thái tổng hợp phải dựa trên đánh giá Backend.
  if (
    reading.serverStatus === 'NORMAL' &&
    ['LOW', 'NONE'].includes(reading.riskLevel) &&
    reading.isDrowsy === false
  ) {
    return {
      code: 'NORMAL',
      label: 'Giám sát bình thường',
      tone: 'normal',
    }
  }

  return {
    code: 'UNKNOWN',
    label: 'Chưa xác định',
    tone: 'unknown',
  }
}

export function normalizeHistoryPage(page) {
  if (!page || !Array.isArray(page.content)) {
    return []
  }

  return page.content
    .map((raw) => normalizeLiveReading(raw))
    .filter((reading) => reading?.measuredAtMs !== null)
    .sort((a, b) => a.measuredAtMs - b.measuredAtMs)
}
