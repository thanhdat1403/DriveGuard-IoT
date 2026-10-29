
import mqtt from 'mqtt'

const MQTT_URL = 'ws://localhost:9001'
const SENSOR_TOPIC = 'driveguard/sensors'

let client = null
let currentSensorCallback = null
let currentStatusCallback = null

export function connectMQTT(onSensorData, onStatusChange) {
  currentSensorCallback = onSensorData
  currentStatusCallback = onStatusChange

  if (client) {
    if (client.connected) {
      currentStatusCallback?.('connected')
    }
    return client
  }

  currentStatusCallback?.('connecting')

  client = mqtt.connect(MQTT_URL, {
    clientId: `driveguard_web_${Math.random().toString(16).slice(2, 10)}`,
    clean: true,
    connectTimeout: 15000,
    reconnectPeriod: 3000,
    protocolVersion: 4,
  })

  client.on('connect', () => {
    console.log('MQTT đã kết nối WebSocket thành công')
    currentStatusCallback?.('connected')

    client.subscribe(SENSOR_TOPIC, (error) => {
      if (error) {
        console.error('Không thể đăng ký topic MQTT:', error)
        return
      }

      console.log('Đã đăng ký topic:', SENSOR_TOPIC)
    })
  })

  client.on('message', (topic, payload) => {
    if (topic !== SENSOR_TOPIC) return

    try {

        const message = payload.toString()

        console.log('MQTT nhận dữ liệu thô:', message)

        const data = JSON.parse(message)

        console.log('MQTT nhận dữ liệu hợp lệ:', data)
        currentSensorCallback?.(data)
    } catch (error) {
      console.error('Dữ liệu MQTT không phải JSON hợp lệ:', error)
    }
  })

  client.on('reconnect', () => {
    console.log('MQTT đang kết nối lại...')
    currentStatusCallback?.('connecting')
  })

  client.on('offline', () => {
    console.warn('MQTT đang offline')
    currentStatusCallback?.('disconnected')
  })

  client.on('error', (error) => {
    console.error('Lỗi kết nối MQTT:', error)
    currentStatusCallback?.('disconnected')
  })

  client.on('close', () => {
    console.warn('Kết nối MQTT đã đóng')
    currentStatusCallback?.('disconnected')
  })

  return client
}

export function disconnectMqtt() {
  if (client) {
    const oldClient = client
    client = null

    oldClient.removeAllListeners()
    oldClient.end(true)
  }

  currentSensorCallback = null
  currentStatusCallback = null
}
