
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  Polyline,
  useMap,
} from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import './GpsTracking.css'

import {
  getLatestDeviceLocation,
  getDeviceLocationHistory,
} from '../../services/api/deviceLocationApi.js'

import {
  normalizeGpsLocation,
  normalizeGpsHistory,
  parseGpsUtc,
  formatGpsTime,
} from '../../services/gpsAdapter.js'

const DEVICE_ID = 2
const POLL_MS = 15000
const GPS_LIVE_MS = 45000
const HISTORY_SIZE = 100

const MAPTILER_KEY = import.meta.env.VITE_MAPTILER_API_KEY


function createCarIcon(headingDeg) {
  // 0 độ là hướng Bắc; góc tăng theo chiều kim đồng hồ.
  const heading = (
    typeof headingDeg === 'number' &&
    Number.isFinite(headingDeg) &&
    headingDeg >= 0 &&
    headingDeg < 360
  )
    ? headingDeg
    : 0

  return L.divIcon({
    className: 'dg-car-marker',
    iconSize: [48, 48],
    iconAnchor: [24, 24],
    popupAnchor: [0, -25],
    html: `
      <div class="dg-car-marker-body">
        <div
          class="dg-car-marker-rotator"
          style="transform: rotate(${heading}deg)"
        >
          <svg
            width="28"
            height="36"
            viewBox="0 0 28 36"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <path
              d="M14 1 L18 7 H10 Z"
              fill="#38bdf8"
            />
            <rect
              x="5" y="7" width="18" height="26"
              rx="6" fill="#2563eb"
              stroke="#ffffff" stroke-width="2"
            />
            <rect
              x="8" y="11" width="12" height="7"
              rx="2" fill="#bae6fd"
            />
            <rect
              x="8" y="23" width="12" height="6"
              rx="2" fill="#0f172a"
            />
            <rect x="2" y="12" width="4" height="7" rx="2" fill="#0f172a"/>
            <rect x="22" y="12" width="4" height="7" rx="2" fill="#0f172a"/>
            <rect x="2" y="25" width="4" height="6" rx="2" fill="#0f172a"/>
            <rect x="22" y="25" width="4" height="6" rx="2" fill="#0f172a"/>
          </svg>
        </div>
      </div>
    `,
  })
}


function formatNumber(value, unit = '') {
  return typeof value === 'number' && Number.isFinite(value)
    ? `${value}${unit}`
    : '—'
}

// Chỉ tự động theo xe khi người dùng bật chế độ này.
function MapFollower({ position, enabled }) {
  const map = useMap()
  const latitude = position?.[0]
  const longitude = position?.[1]

  useEffect(() => {
    if (!enabled || latitude == null || longitude == null) return

    map.panTo([latitude, longitude], {
      animate: true,
      duration: 0.5,
    })
  }, [map, enabled, latitude, longitude])

  return null
}

function getGpsStatus(location, now) {
  if (!location) {
    return { label: 'CHƯA CÓ GPS', type: 'unknown' }
  }

  if (now === null) {
    return { label: 'ĐANG XÁC ĐỊNH', type: 'unknown' }
  }

  const measuredAt = parseGpsUtc(location.measuredAt)

  if (!Number.isFinite(measuredAt)) {
    return { label: 'KHÔNG RÕ THỜI GIAN', type: 'unknown' }
  }

  const age = now - measuredAt

  // Dữ liệu tương lai không được coi là GPS LIVE.
  if (age >= 0 && age <= GPS_LIVE_MS) {
    return { label: 'GPS LIVE', type: 'live' }
  }

  return { label: 'LAST KNOWN', type: 'stale' }
}

export default function GpsTracking() {
  const [location, setLocation] = useState(null)
  const [gpsHistory, setGpsHistory] = useState([])
  const [historyCount, setHistoryCount] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [updatedAt, setUpdatedAt] = useState(null)
  const [followVehicle, setFollowVehicle] = useState(true)
  const [now, setNow] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)


  // Đồng bộ đồng hồ sau khi component được mount.
  useEffect(() => {
    const updateClock = () => {
      setNow(Date.now())
    }

    updateClock()

    const timer = setInterval(updateClock, 1000)

    return () => clearInterval(timer)
  }, [])


  useEffect(() => {
    const controller = new AbortController()
    let inFlight = false

    async function refresh() {
      if (inFlight || controller.signal.aborted) return

      inFlight = true
      setRefreshing(true)

      try {
        const [latestResponse, historyResponse] = await Promise.all([
          getLatestDeviceLocation(DEVICE_ID, {
            signal: controller.signal,
          }),
          getDeviceLocationHistory(DEVICE_ID, {
            page: 0,
            size: HISTORY_SIZE,
            signal: controller.signal,
          }),
        ])

        if (controller.signal.aborted) return

        const latest = normalizeGpsLocation(latestResponse)

        if (!latest) {
          throw new Error('Backend trả về tọa độ GPS không hợp lệ')
        }

        const history = normalizeGpsHistory(historyResponse)

        setLocation(latest)
        setGpsHistory(history)
        setHistoryCount(
          historyResponse.totalElements ?? history.length,
        )
        setUpdatedAt(new Date())
        setNow(Date.now())
        setError('')
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || 'Không tải được dữ liệu GPS')
        }
      } finally {
        inFlight = false

        if (!controller.signal.aborted) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    refresh()

    const interval = setInterval(refresh, POLL_MS)

    return () => {
      controller.abort()
      clearInterval(interval)
    }
  }, [reloadKey])

  const handleRefresh = useCallback(() => {
    if (!refreshing) {
      setReloadKey((value) => value + 1)
    }
  }, [refreshing])

  const position = useMemo(
    () => location
      ? [location.latitude, location.longitude]
      : null,
    [location],
  )

  const routePoints = useMemo(
    () => gpsHistory.map((item) => [
      item.latitude,
      item.longitude,
    ]),
    [gpsHistory],
  )

  const gpsStatus = getGpsStatus(location, now)


  // Chỉ tạo lại icon khi hướng xe thay đổi.
  const carIcon = useMemo(
    () => createCarIcon(location?.headingDeg),
    [location?.headingDeg],
  )


  const mapUrl = MAPTILER_KEY
    ? `https://api.maptiler.com/maps/streets-v2/256/{z}/{x}/{y}.png?key=${MAPTILER_KEY}`
    : null

  return (
    <section className="dg-gps-panel">
      <header className="dg-gps-heading">
        <div>
          <span className="dg-gps-eyebrow">
            GPS / SPRING BOOT / E2E
          </span>

          <h2>Giám sát vị trí thiết bị</h2>

          <p>
            Thiết bị #{DEVICE_ID} · Dữ liệu từ MariaDB E2E
          </p>
        </div>

        <div className="dg-gps-actions">
          <span
            className={`dg-gps-badge dg-gps-status-${gpsStatus.type}`}
          >
            {loading
              ? 'ĐANG TẢI'
              : error
                ? 'LỖI CẬP NHẬT'
                : gpsStatus.label}
          </span>

          <button
            type="button"
            className="dg-gps-button"
            onClick={() => setFollowVehicle((value) => !value)}
          >
            {followVehicle ? 'Tắt theo xe' : 'Theo xe'}
          </button>

          <button
            type="button"
            className="dg-gps-button"
            onClick={handleRefresh}
            disabled={refreshing}
          >
            {refreshing ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </header>

      {error && (
        <p className="dg-gps-error" role="alert">
          {error}. Dữ liệu đã tải trước đó vẫn được giữ lại.
        </p>
      )}

      {!mapUrl && (
        <p className="dg-gps-error" role="alert">
          Chưa cấu hình VITE_MAPTILER_API_KEY.
        </p>
      )}

      {position && mapUrl ? (
        <div className="dg-gps-map">
          <MapContainer
            center={position}
            zoom={15}
            minZoom={5}
            maxZoom={19}
            scrollWheelZoom
            dragging
            doubleClickZoom
            zoomControl
            className="dg-gps-map-container"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.maptiler.com/copyright/">MapTiler</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
              url={mapUrl}
              tileSize={256}
              maxZoom={19}
            />

            <MapFollower
              position={position}
              enabled={followVehicle}
            />

            {routePoints.length >= 2 && (
              <Polyline
                positions={routePoints}
                pathOptions={{
                  color: '#2563eb',
                  weight: 5,
                  opacity: 0.9,
                  lineCap: 'round',
                  lineJoin: 'round',
                }}
              />
            )}

            <Marker position={position} icon={carIcon}>
              <Popup>
                <strong>
                  DriveGuard — Thiết bị #{DEVICE_ID}
                </strong>

                <div>
                  {location.latitude}, {location.longitude}
                </div>

                <div>
                  Tốc độ: {formatNumber(location.speedKmh, ' km/h')}
                </div>

                <div>
                  Đo lúc: {formatGpsTime(location.measuredAt)}
                </div>
              </Popup>
            </Marker>
          </MapContainer>
        </div>
      ) : (
        <div className="dg-gps-empty">
          {loading
            ? 'Đang tải bản đồ GPS...'
            : !mapUrl
              ? 'Thiếu cấu hình MapTiler.'
              : 'Chưa có vị trí GPS hợp lệ.'}
        </div>
      )}

      <div className="dg-gps-stats">
        <article>
          <span>Vĩ độ</span>
          <strong>{formatNumber(location?.latitude, '°')}</strong>
        </article>

        <article>
          <span>Kinh độ</span>
          <strong>{formatNumber(location?.longitude, '°')}</strong>
        </article>

        <article>
          <span>Tốc độ ghi nhận</span>
          <strong>{formatNumber(location?.speedKmh, ' km/h')}</strong>
        </article>

        <article>
          <span>Hướng di chuyển</span>
          <strong>{formatNumber(location?.headingDeg, '°')}</strong>
        </article>

        <article>
          <span>Độ chính xác khai báo</span>
          <strong>{formatNumber(location?.accuracyM, ' m')}</strong>
        </article>

        <article>
          <span>Tổng bản ghi GPS</span>
          <strong>{historyCount ?? '—'}</strong>
        </article>
      </div>

      <div className="dg-gps-footer">
        <p>
          Thời điểm đo (Việt Nam):{' '}
          {formatGpsTime(location?.measuredAt)}
        </p>

        <p>
          Thời điểm nhận (Việt Nam):{' '}
          {formatGpsTime(location?.receivedAt)}
        </p>

        <p>
          API tải lúc:{' '}
          {updatedAt
            ? updatedAt.toLocaleTimeString('vi-VN')
            : '—'}
          {' '}· Chu kỳ: {POLL_MS / 1000} giây
        </p>

        <p>
          Hiển thị {gpsHistory.length} điểm gần nhất
          {' '}· Tổng {historyCount ?? '—'} bản ghi
        </p>

        <p>
          GPS LIVE: dữ liệu đo trong {GPS_LIVE_MS / 1000} giây
          gần nhất. Đây không phải trạng thái kết nối thiết bị.
        </p>
      </div>
    </section>
  )
}
