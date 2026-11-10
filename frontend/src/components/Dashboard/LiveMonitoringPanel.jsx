import { useEffect, useState } from 'react'
import { getLatestSensorReading } from '../../services/api/sensorReadingApi.js'
import { normalizeLiveReading, getMonitoringStatus } from '../../services/liveSensorAdapter.js'
import './LiveMonitoringPanel.css'

const DEVICE_ID = 1 // Chỉ dùng cho Dashboard ADMIN thử nghiệm
const POLL_MS = 5000

const showNumber = (value, suffix = '') =>
  value === null || value === undefined ? '—' : `${value}${suffix}`

export default function LiveMonitoringPanel() {
  const [raw, setRaw] = useState(null)
  const [now, setNow] = useState(Date.now())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    let inFlight = false

    const load = async () => {
      if (inFlight) return
      inFlight = true
      try {
        const result = await getLatestSensorReading(DEVICE_ID, {
          signal: controller.signal,
        })
        if (!controller.signal.aborted) {
          setRaw(result)
          setError('')
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || 'Không tải được dữ liệu cảm biến')
        }
      } finally {
        inFlight = false
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    load()
    const pollId = setInterval(load, POLL_MS)
    const clockId = setInterval(() => setNow(Date.now()), 1000)
    return () => {
      controller.abort()
      clearInterval(pollId)
      clearInterval(clockId)
    }
  }, [])

  const reading = normalizeLiveReading(raw, { now })
  const status = getMonitoringStatus(reading)
  const usable = Boolean(reading?.isFresh && !error)
  const cards = [
    ['Nhịp tim', usable ? showNumber(reading.heartRate, ' BPM') : '—', 'heartRate'],
    ['SpO₂', usable ? showNumber(reading.spo2, '%') : '—', 'spo2'],
    ['AI ngủ gật', usable ? (reading.edgeAiStatus || '—') : '—', 'edgeAiStatus'],
    ['Mức rủi ro', usable ? (reading.riskLevel || '—') : '—', 'riskLevel'],
    ['Góc cúi đầu', '—', 'headTiltDegrees'],
    ['Thời gian nhắm mắt', '—', 'eyeClosureDurationMs'],
    ['Trạng thái mắt', '—', 'eyeStatus'],
    ['Tư thế đầu', '—', 'headStatus'],
  ]

  const visibleStatus = error
    ? 'Không cập nhật được dữ liệu từ Backend'
    : loading
      ? 'Đang tải dữ liệu'
      : status.label

  return (
    <section className="dg-live-panel" aria-label="Giám sát cảm biến từ Spring Boot">
      <div className="dg-live-heading">
        <div>
          <span className="dg-live-eyebrow">SPRING BOOT / MARIADB</span>
          <h2>Giám sát cảm biến thực tế</h2>
          <p>Thiết bị #{DEVICE_ID} · Dữ liệu từ API có xác thực JWT</p>
        </div>
        <span className={`dg-live-state ${usable ? 'dg-live-fresh' : 'dg-live-unknown'}`}>
          {visibleStatus}
        </span>
      </div>
      {error && <p className="dg-live-error" role="alert">{error}</p>}
      <div className="dg-live-grid">
        {cards.map(([label, value, key]) => (
          <article className="dg-live-card" key={key}>
            <span className="dg-live-label">{label}</span>
            <strong className="dg-live-value">{value}</strong>
            <span className="dg-live-note">
              {['headTiltDegrees', 'eyeClosureDurationMs', 'eyeStatus', 'headStatus'].includes(key)
                ? 'Backend chưa cung cấp trường này'
                : !usable ? 'Không có dữ liệu LIVE hợp lệ' : 'Từ bản ghi cảm biến'}
            </span>
          </article>
        ))}
      </div>
      <div className="dg-live-footer">
        <span>Thiết bị: {reading?.deviceCode || '—'}</span>
        <span>Thời điểm đo (giá trị Backend): {reading?.measuredAt || '—'}</span>
        <span>Làm mới API mỗi 5 giây · Ngưỡng quá hạn tạm thời 15 giây</span>
      </div>
      <p className="dg-live-disclaimer">Trạng thái ở đây không phải chẩn đoán y khoa. Kết nối MQTT trên trình duyệt không đồng nghĩa thiết bị đang truyền dữ liệu mới.</p>
    </section>
  )
}
