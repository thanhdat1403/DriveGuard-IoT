import { useEffect, useMemo, useState } from 'react'
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'
import { getSensorReadingHistory } from '../../services/api/sensorReadingApi.js'
import { normalizeHistoryPage } from '../../services/liveSensorAdapter.js'
import './VitalSignsChart.css'

const DEVICE_ID = 1
const PAGE_SIZE = 100
const REFRESH_MS = 15000

function displayTime(value) {
  if (!Number.isFinite(value)) return 'Không rõ thời gian'
  return new Date(value).toLocaleString('vi-VN', { hour12: false, timeZone: 'Asia/Ho_Chi_Minh' })
}

export default function VitalSignsChart() {
  const [readings, setReadings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [updatedAt, setUpdatedAt] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    let running = false
    async function refresh() {
      if (running) return
      running = true
      try {
        const page = await getSensorReadingHistory(DEVICE_ID, {
          page: 0, size: PAGE_SIZE, signal: controller.signal,
        })
        if (!controller.signal.aborted) {
          if (!Array.isArray(page?.content)) throw new Error('API không trả danh sách content hợp lệ')
          setReadings(normalizeHistoryPage(page))
          setUpdatedAt(Date.now())
          setError('')
        }
      } catch (err) {
        if (!controller.signal.aborted) setError(err.message || 'Không lấy được lịch sử cảm biến')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
        running = false
      }
    }
    refresh()
    const interval = setInterval(refresh, REFRESH_MS)
    return () => { controller.abort(); clearInterval(interval) }
  }, [])

  const chartData = useMemo(() => readings.map(item => ({
    time: item.measuredAtMs,
    heartRate: item.heartRate,
    spo2: item.spo2,
  })), [readings])
  const validBpm = chartData.filter(item => item.heartRate !== null).length
  const validSpo2 = chartData.filter(item => item.spo2 !== null).length

  return (
    <section className="dg-vitals-panel" aria-label="Biểu đồ lịch sử sinh hiệu">
      <div className="dg-vitals-header">
        <div>
          <h2>Biểu đồ lịch sử nhịp tim và SpO₂</h2>
          <p>Thiết bị #{DEVICE_ID} · Tối đa {PAGE_SIZE} bản ghi từ API · Làm mới 15 giây</p>
        </div>
        <span className="dg-vitals-historical">DỮ LIỆU LỊCH SỬ · KHÔNG PHẢI LIVE</span>
      </div>
      {error && <p className="dg-vitals-error" role="alert">Lỗi tải dữ liệu: {error}. Đồ thị (nếu có) là dữ liệu tải trước đó.</p>}
      {loading ? <p>Đang tải lịch sử cảm biến…</p> : chartData.length === 0 ? (
        <p>Chưa có bản ghi lịch sử để hiển thị.</p>
      ) : (
        <>
          <div className="dg-vitals-chart">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 12, right: 12, bottom: 8, left: 0 }}>
                <CartesianGrid stroke="#304158" strokeDasharray="3 3" />
                <XAxis dataKey="time" type="number" domain={['dataMin', 'dataMax']} tickFormatter={v => new Date(v).toLocaleTimeString('vi-VN', {hour:'2-digit',minute:'2-digit',timeZone:'Asia/Ho_Chi_Minh'})} stroke="#9db2cc" minTickGap={35} />
                <YAxis yAxisId="bpm" orientation="left" stroke="#fb7185" domain={[0, 220]} label={{value:'BPM',angle:-90,position:'insideLeft',fill:'#fb7185'}} />
                <YAxis yAxisId="spo2" orientation="right" stroke="#5eead4" domain={[0, 100]} label={{value:'SpO₂ %',angle:90,position:'insideRight',fill:'#5eead4'}} />
                <Tooltip labelFormatter={displayTime} formatter={(value, name) => [value == null ? 'Không có số đo' : `${value} ${name === 'Nhịp tim' ? 'BPM' : '%'}`, name]} contentStyle={{background:'#142238',border:'1px solid #38506b',borderRadius:8,color:'#eaf2ff'}} />
                <Legend />
                <Line yAxisId="bpm" type="linear" dataKey="heartRate" name="Nhịp tim" stroke="#fb7185" strokeWidth={2} dot={false} connectNulls={false} isAnimationActive={false} />
                <Line yAxisId="spo2" type="linear" dataKey="spo2" name="SpO₂" stroke="#5eead4" strokeWidth={2} dot={false} connectNulls={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <p className="dg-vitals-footnote">Số đo hợp lệ: BPM {validBpm}/{chartData.length} · SpO₂ {validSpo2}/{chartData.length}. Giá trị 0 hoặc không hợp lệ được bỏ trống, không nối đường qua khoảng thiếu dữ liệu.</p>
          <p className="dg-vitals-footnote">Khoảng bản ghi: {displayTime(chartData[0].time)} — {displayTime(chartData[chartData.length - 1].time)}. {updatedAt ? `Tải API lúc ${displayTime(updatedAt)}.` : ''}</p>
          <p className="dg-vitals-footnote">Múi giờ của timestamp Backend chưa được xác minh; biểu đồ tạm sử dụng quy ước UTC của adapter.</p>
        </>
      )}
    </section>
  )
}
