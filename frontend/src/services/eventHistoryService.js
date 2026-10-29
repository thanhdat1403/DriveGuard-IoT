
const MAX_EVENTS = 50 // Giới hạn 50 sự kiện và chống ghi trùng của bạn, đồng thời bổ sung quản lý sự kiện đang hoạt động
const STORAGE_KEY = 'driveguard_event_history'
const DUPLICATE_WINDOW_MS = 5000

let eventHistory = loadEventHistory()
let previousEventKey = null
let previousEventTime = 0


function loadEventHistory() {
  try {
    const savedEvents = localStorage.getItem(STORAGE_KEY)

    if (!savedEvents) return []

    const parsedEvents = JSON.parse(savedEvents)

    if (!Array.isArray(parsedEvents)) return []

    const now = Date.now()

    const normalizedEvents = parsedEvents
      .filter((event) => event && typeof event === 'object')
      .map((event) => {
        const timestampMs = Date.parse(event.timestamp)
        const startedAtMs = Date.parse(
          event.startedAt || event.timestamp
        )

        const timestamp = Number.isFinite(timestampMs)
          ? new Date(timestampMs).toISOString()
          : new Date(now).toISOString()

        const startedAt = Number.isFinite(startedAtMs)
          ? new Date(startedAtMs).toISOString()
          : timestamp

        const validSource =
          typeof event.source === 'string' &&
          event.source.trim() !== ''
            ? event.source
            : 'UNKNOWN'

        const validLevel = [
          'warning',
          'critical',
          'unknown',
        ].includes(event.level)
          ? event.level
          : 'unknown'

        const validStatus = [
          'ACTIVE',
          'RESOLVED',
        ].includes(event.status)
          ? event.status
          : 'RESOLVED'

        const endedAtMs = Date.parse(event.endedAt)

        const endedAt =
          validStatus === 'RESOLVED'
            ? Number.isFinite(endedAtMs)
              ? new Date(endedAtMs).toISOString()
              : timestamp
            : null

        const durationMs =
          validStatus === 'RESOLVED'
            ? Number.isFinite(Number(event.durationMs)) &&
              Number(event.durationMs) >= 0
              ? Number(event.durationMs)
              : Math.max(
                  0,
                  Date.parse(endedAt) - Date.parse(startedAt)
                )
            : null

        return {
          ...event,
          id:
            typeof event.id === 'string' && event.id
              ? event.id
              : `${Date.parse(startedAt)}-${Math.random()
                  .toString(16)
                  .slice(2)}`,
          timestamp,
          startedAt,
          endedAt,
          durationMs,
          status: validStatus,
          source: validSource,
          level: validLevel,
        }
      })

    const limitedEvents = normalizedEvents.slice(0, MAX_EVENTS)

    // Lưu lại dữ liệu sau khi chuẩn hóa
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(limitedEvents)
      )
    } catch (error) {
      console.error('Không thể lưu lịch sử đã chuẩn hóa:', error)
    }

    return limitedEvents
  } catch (error) {
    console.error('Không thể đọc lịch sử sự kiện:', error)
    return []
  }
}


function saveEventHistory() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(eventHistory)
    )
  } catch (error) {
    console.error('Không thể lưu lịch sử sự kiện:', error)
  }
}

function getIncidentInfo(eyeStatus, headStatus) {
  if (eyeStatus === 'critical' || headStatus === 'critical') {
    return {
      level: 'critical',
      title: 'Cảnh báo mức cao',
      message: 'Dữ liệu cảm biến ghi nhận trạng thái cần được kiểm tra.',
    }
  }

  if (eyeStatus === 'warning' || headStatus === 'warning') {
    return {
      level: 'warning',
      title: 'Phát hiện dấu hiệu ngủ gật',
      message:
        'Dữ liệu cảm biến ghi nhận dấu hiệu bất thường ở mắt hoặc tư thế đầu.',
    }
  }

  if (eyeStatus === 'unknown' || headStatus === 'unknown') {
    return {
      level: 'unknown',
      title: 'Chưa xác định trạng thái',
      message:
        'Dữ liệu chưa đủ để xác định trạng thái mắt hoặc tư thế đầu.',
    }
  }

  return null
}

function getEventTime(sensorData) {
  const timestamp = sensorData.timestamp
  const parsedTime = typeof timestamp === 'string'
    ? Date.parse(timestamp)
    : NaN

  return Number.isFinite(parsedTime)
    ? parsedTime
    : Date.now()
}

function resolveActiveEvents(now, source) {
  let changed = false

  eventHistory = eventHistory.map((event) => {
    if (
      event.status === 'ACTIVE' &&
      (source == null || event.source === source)
    ) {
      const startedAt = Date.parse(event.startedAt || event.timestamp)
      const validStartedAt = Number.isFinite(startedAt)
        ? startedAt
        : now

      changed = true

      return {
        ...event,
        status: 'RESOLVED',
        endedAt: new Date(now).toISOString(),
        durationMs: Math.max(0, now - validStartedAt),
      }
    }

    return event
  })

  if (changed) {
    saveEventHistory()
  }

  return [...eventHistory]
}


export function recordSensorEvent(sensorData) {
  const eyeStatus = sensorData.eyeStatus ?? 'unknown'
  const headStatus = sensorData.headStatus ?? 'unknown'
  const source = sensorData.source ?? 'UNKNOWN'
  const now = getEventTime(sensorData)
  const eventInfo = getIncidentInfo(eyeStatus, headStatus)

  // Khi dữ liệu trở lại bình thường, kết thúc các sự kiện đang hoạt động.
  if (!eventInfo) {
    previousEventKey = null
    previousEventTime = 0
    return resolveActiveEvents(now, source)
  }

  const { level, title, message } = eventInfo
  const eventKey = `${source}:${level}:${eyeStatus}:${headStatus}`

  // Tìm sự kiện đang hoạt động thuộc cùng nguồn dữ liệu.
  const activeEvents = eventHistory.filter(
    (event) => event.status === 'ACTIVE' && event.source === source
  )

  // Nếu đã có sự kiện cùng mức cảnh báo, giữ sự kiện đó,
  // đồng thời kết thúc các sự kiện ACTIVE khác cùng nguồn.
  const matchingActiveEvent = activeEvents.find(
    (event) => event.level === level
  )

  if (matchingActiveEvent) {
    const nowIso = new Date(now).toISOString()
    let changed = false

    eventHistory = eventHistory.map((event) => {
      if (
        event.status === 'ACTIVE' &&
        event.source === source &&
        event.id !== matchingActiveEvent.id
      ) {
        const startedAt = Date.parse(event.startedAt || event.timestamp)
        changed = true

        return {
          ...event,
          status: 'RESOLVED',
          endedAt: nowIso,
          durationMs: Number.isFinite(startedAt)
            ? Math.max(0, now - startedAt)
            : 0,
        }
      }

      return event
    })

    if (changed) {
      saveEventHistory()
    }

    previousEventKey = eventKey
    previousEventTime = now

    return [...eventHistory]
  }

  // Mức cảnh báo đã thay đổi: kết thúc toàn bộ sự kiện ACTIVE
  // cùng nguồn trước khi tạo sự kiện mới.
  resolveActiveEvents(now, source)

  // Chống ghi nhận trùng lặp trong khoảng thời gian ngắn.
  if (
    eventKey === previousEventKey &&
    now - previousEventTime < DUPLICATE_WINDOW_MS
  ) {
    return [...eventHistory]
  }

  previousEventKey = eventKey
  previousEventTime = now

  const startedAt = new Date(now).toISOString()

  const event = {
    id: `${now}-${Math.random().toString(16).slice(2)}`,
    timestamp: startedAt,
    startedAt,
    endedAt: null,
    durationMs: null,
    status: 'ACTIVE',
    source,
    level,
    title,
    message,
    eyeStatus,
    headStatus,
    heartRate: sensorData.heartRate ?? null,
    eyeClosureDurationMs: sensorData.eyeClosureDurationMs ?? null,
    headTiltDegrees: sensorData.headTiltDegrees ?? null,
  }

  eventHistory = [event, ...eventHistory].slice(0, MAX_EVENTS)
  saveEventHistory()

  return [...eventHistory]
}


export function getEventHistory() {
  return [...eventHistory]
}
