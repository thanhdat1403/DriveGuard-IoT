
const validScenarios = ['normal', 'drowsy', 'unresponsive']
const validStatuses = ['normal', 'warning', 'critical', 'unknown']

function normalizeEyeStatus(value) {
  const status = String(value ?? '').trim().toUpperCase()

  switch (status) {
    case 'OPEN':
    case 'NORMAL':
      return 'normal'

    case 'CLOSED':
    case 'DROWSY':
    case 'SLEEPY':
    case 'WARNING':
      return 'warning'

    case 'LONG_CLOSURE':
    case 'UNRESPONSIVE':
    case 'CRITICAL':
      return 'critical'

    default:
      return validStatuses.includes(String(value).toLowerCase())
        ? String(value).toLowerCase()
        : 'unknown'
  }
}

function normalizeHeadStatus(value) {
  const status = String(value ?? '').trim().toUpperCase()

  switch (status) {
    case 'NORMAL':
    case 'STABLE':
      return 'normal'

    case 'TILTED':
    case 'HEAD_DOWN':
    case 'WARNING':
      return 'warning'

    case 'UNRESPONSIVE':
    case 'CRITICAL':
      return 'critical'

    default:
      return validStatuses.includes(String(value).toLowerCase())
        ? String(value).toLowerCase()
        : 'unknown'
  }
}

export function normalizeSensorData(rawData = {}) {
  const scenario = validScenarios.includes(
    String(rawData.scenario ?? '').toLowerCase()
  )
    ? String(rawData.scenario).toLowerCase()
    : 'normal'

  const eyeClosureDurationMs =
    typeof rawData.eyeClosureDurationMs === 'number' &&
    Number.isFinite(rawData.eyeClosureDurationMs) &&
    rawData.eyeClosureDurationMs >= 0
      ? rawData.eyeClosureDurationMs
      : null

  const headTiltDegrees =
    typeof rawData.headTiltDegrees === 'number' &&
    Number.isFinite(rawData.headTiltDegrees)
      ? rawData.headTiltDegrees
      : null

  const heartRate =
    typeof rawData.heartRate === 'number' &&
    Number.isFinite(rawData.heartRate) &&
    rawData.heartRate > 0
      ? rawData.heartRate
      : null

  const timestamp =
    typeof rawData.timestamp === 'string' &&
    !Number.isNaN(Date.parse(rawData.timestamp))
      ? rawData.timestamp
      : new Date().toISOString()

  return {
    timestamp,
    source: typeof rawData.source === 'string'
      ? rawData.source
      : 'UNKNOWN',
    scenario,
    eyeStatus: normalizeEyeStatus(rawData.eyeStatus),
    headStatus: normalizeHeadStatus(rawData.headStatus),
    eyeClosureDurationMs,
    headTiltDegrees,
    heartRate,
    mqttConnected: rawData.mqttConnected === true,
  }
}
