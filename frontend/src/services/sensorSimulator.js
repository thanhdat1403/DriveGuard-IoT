
const randomBetween = (min, max) =>
  Math.floor(Math.random() * (max - min + 1)) + min

export function generateSensorData(scenario = 'normal') {
  const timestamp = new Date().toISOString()

  const baseData = {
    timestamp,
    source: 'SIMULATOR',
    scenario,
    eyeStatus: 'normal',
    headStatus: 'normal',
    mqttConnected: false,
  }

  switch (scenario) {
    case 'drowsy':
      return {
        ...baseData,
        eyeStatus: 'warning',
        headStatus: 'warning',
        eyeClosureDurationMs: randomBetween(1800, 3500),
        headTiltDegrees: randomBetween(20, 35),
        heartRate: randomBetween(75, 100),
      }

    case 'unresponsive':
      return {
        ...baseData,
        eyeStatus: 'unknown',
        headStatus: 'critical',
        eyeClosureDurationMs: null,
        headTiltDegrees: randomBetween(30, 50),
        heartRate: randomBetween(50, 120),
      }

    default:
      return {
        ...baseData,
        eyeStatus: 'normal',
        headStatus: 'normal',
        eyeClosureDurationMs: randomBetween(100, 400),
        headTiltDegrees: randomBetween(0, 8),
        heartRate: randomBetween(65, 85),
      }
  }
}
