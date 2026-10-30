import { useEffect, useState } from 'react'
import './App.css'
import { generateSensorData } from './services/sensorSimulator'
import { normalizeSensorData } from './services/sensorDataAdapter'
import { connectMQTT, disconnectMqtt } from './services/mqttService'
import {
  recordSensorEvent,
  getEventHistory,
} from './services/eventHistoryService'

  const sensorData = [
    {
      label: 'Trạng thái mắt',
      detail: 'Dữ liệu mô phỏng',
      status: 'normal',
      icon: '◉',
      sensorType: 'eye',
    },
    {
      label: 'Tư thế đầu',
      detail: 'Dữ liệu mô phỏng',
      status: 'normal',
      icon: '⌁',
      sensorType: 'head',
    },
    {
      label: 'Nhịp tim',
      value: '-- BPM',
      detail: 'Chưa kết nối cảm biến',
      status: 'offline',
      icon: '♡',
      sensorType: 'heart',
    },
    {
      label: 'Kết nối MQTT',
      value: 'Chưa kết nối',
      detail: 'Sẽ tích hợp ở bước tiếp theo',
      status: 'offline',
      icon: '⌘',
      sensorType: 'mqtt',
    },
  ]

  const scenarios = {
    normal: {
      label: 'Bình thường',
      eye: 'Bình thường',
      head: 'Ổn định',
      alertTitle: 'Không có cảnh báo',
      alertMessage: 'Dữ liệu mô phỏng cho thấy trạng thái bình thường.',
      alertLevel: 'normal',
      bannerStatus: 'AN TOÀN · DEMO',
      eyeStatus: 'normal',
      headStatus: 'normal',
      eventTitle: 'Trạng thái bình thường',
      eventMessage: 'Hệ thống đang chạy ở chế độ mô phỏng.',
    },

    drowsy: {
      label: 'Mô phỏng ngủ gật',
      eye: 'Dấu hiệu buồn ngủ',
      head: 'Cúi đầu mô phỏng',
      alertTitle: 'Cảnh báo ngủ gật',
      alertMessage:
        'Đã kích hoạt tình huống ngủ gật giả lập để kiểm tra phản hồi của giao diện.',
      alertLevel: 'warning',
      bannerStatus: 'CẢNH BÁO · DEMO',
      eyeStatus: 'warning',
      headStatus: 'warning',
      eventTitle: 'Phát hiện dấu hiệu ngủ gật',
      eventMessage: 'Tình huống cảnh báo đang được mô phỏng.',
    },

    unresponsive: {
      label: 'Mô phỏng không phản hồi',
      eye: 'Không xác định',
      head: 'Không phản hồi',
      alertTitle: 'Cảnh báo mức cao',
      alertMessage:
        'Tình huống tài xế không phản hồi đang được mô phỏng. Đây chưa phải kết quả phát hiện y tế thực tế.',
      alertLevel: 'critical',
      bannerStatus: 'MỨC CAO · DEMO',
      eyeStatus: 'critical',
      headStatus: 'critical',
      eventTitle: 'Mô phỏng tài xế không phản hồi',
      eventMessage: 'Đang kiểm tra giao diện cảnh báo mức cao.',
    },
  }

  // Hàm xác định mức cảnh báo (Hàm này giúp thống nhất quy tắc phân loại giữa dữ liệu MQTT và các bước quản lý cảnh báo tiếp theo)
  const getIncidentLevel = (data) => {
    const { eyeStatus, headStatus } = data

    if (eyeStatus === 'critical' || headStatus === 'critical') {
      return 'critical'
    }

    if (eyeStatus === 'warning' || headStatus === 'warning') {
      return 'warning'
    }

    if (eyeStatus === 'unknown' || headStatus === 'unknown') {
      return 'unknown'
    }

    return 'normal'
  }

function App({ user, onLogout }) {

  
  const roleNames = {
    ADMIN: 'Quản trị viên',
    VIEWER: 'Người xem',
    HR: 'Nhân sự',
    EMPLOYEE: 'Nhân viên',
    TECH_LEAD: 'Trưởng nhóm kỹ thuật',
  }

  const role = String(user?.role || 'UNKNOWN').toUpperCase()
  const roleLabel = roleNames[role] || role

  const username =
    user?.fullName ||
    user?.fullname ||
    user?.username ||
    user?.email ||
    role

  const avatarText = username.trim().charAt(0).toUpperCase() || 'U'


  const [scenario, setScenario] = useState('normal')

  const [sensorReading, setSensorReading] = useState(() =>
    normalizeSensorData(generateSensorData('normal'))
  )


  const currentScenario = scenarios[scenario]

  const [eventHistory, setEventHistory] = useState(() =>
    getEventHistory()
  )

  const [eventFilter, setEventFilter] = useState('all')

  const filteredEventHistory = eventHistory.filter((event) => {
    if (eventFilter === 'all') return true
    return event.level === eventFilter
  })


  const [mqttStatus, setMqttStatus] = useState('connecting')

  const [dataSource, setDataSource] = useState('SIMULATOR')

  const [hasReceivedMqttData, setHasReceivedMqttData] = useState(false)

  // Biến này sẽ lưu thông tin cảnh báo đang diễn ra
  
  const [activeIncident, setActiveIncident] = useState(() => {
    const activeEvent = getEventHistory().find(
      (event) => event.status === 'ACTIVE'
    )

    if (!activeEvent) {
      return null
    }

    return {
      level: activeEvent.level,
      startedAt: activeEvent.startedAt || activeEvent.timestamp,
    }
  })


  const activeAlert = (() => {
    const eyeStatus = sensorReading.eyeStatus
    const headStatus = sensorReading.headStatus

    // Ưu tiên dữ liệu MQTT đang nhận được.
    if (dataSource === 'MQTT') {
      if (eyeStatus === 'critical' || headStatus === 'critical') {
        return {
          ...scenarios.unresponsive,
          bannerStatus: 'MỨC CAO · MQTT',
          alertMessage:
            'Dữ liệu MQTT ghi nhận trạng thái cần chú ý ở mắt hoặc tư thế đầu. Cần kiểm tra thêm trước khi xác định tình trạng tài xế.',
        }
      }

      if (eyeStatus === 'warning' || headStatus === 'warning') {
        return {
          ...scenarios.drowsy,
          bannerStatus: 'CẢNH BÁO · MQTT',
          alertMessage:
            'Dữ liệu MQTT ghi nhận dấu hiệu bất thường ở mắt hoặc tư thế đầu. Hãy kiểm tra dữ liệu cảm biến và trạng thái tài xế.',
        }
      }

      // Xử lý sự kiện của mắt
      if (eyeStatus === 'unknown' || headStatus === 'unknown') {
        return {
          ...scenarios.unresponsive,
          alertTitle: 'Chưa đủ dữ liệu xác định trạng thái',
          alertLevel: 'unknown',
          bannerStatus: 'CHƯA XÁC ĐỊNH · MQTT',
          alertMessage:
            'Dữ liệu MQTT chưa đủ để xác định trạng thái mắt hoặc tư thế đầu. Không nên kết luận tài xế không phản hồi chỉ từ dữ liệu chưa xác định.',
        }
      }

      return {
        ...scenarios.normal,
        bannerStatus: 'AN TOÀN · MQTT',
        alertMessage:
          'Dữ liệu MQTT hiện không ghi nhận cảnh báo theo các trạng thái được cấu hình.',
      }
    }

    // Khôi phục cảnh báo MQTT còn ACTIVE trong lịch sử khi vừa tải lại trang.
    const savedMqttIncident = getEventHistory().find(
      (event) => event.status === 'ACTIVE' && event.source === 'MQTT'
    )

    if (savedMqttIncident) {
      if (savedMqttIncident.level === 'critical') {
        return {
          ...scenarios.unresponsive,
          bannerStatus: 'MỨC CAO · SỰ KIỆN ĐÃ LƯU',
          alertMessage:
            'Lịch sử ghi nhận một cảnh báo mức cao chưa được đánh dấu kết thúc. Hãy kiểm tra dữ liệu cảm biến mới nhất để xác nhận trạng thái hiện tại.',
        }
      }

      if (savedMqttIncident.level === 'warning') {
        return {
          ...scenarios.drowsy,
          bannerStatus: 'CẢNH BÁO · SỰ KIỆN ĐÃ LƯU',
          alertMessage:
            'Lịch sử ghi nhận một cảnh báo ngủ gật chưa được đánh dấu kết thúc. Hãy kiểm tra dữ liệu cảm biến mới nhất để xác nhận trạng thái hiện tại.',
        }
      }

      if (savedMqttIncident.level === 'unknown') {
        return {
          ...scenarios.unresponsive,
          alertTitle: 'Chưa đủ dữ liệu xác định trạng thái',
          alertLevel: 'unknown',
          bannerStatus: 'CHƯA XÁC ĐỊNH · SỰ KIỆN ĐÃ LƯU',
          alertMessage:
            'Lịch sử ghi nhận dữ liệu chưa đủ để xác định trạng thái. Không nên kết luận tài xế không phản hồi khi chưa có dữ liệu mới.',
        }
      }
    }

    return currentScenario
  })()



  // Kết nối MQTT và disconnectMqtt
  useEffect(() => {
    connectMQTT(
      (rawData) => {
        const normalizedData = {
          ...normalizeSensorData(rawData),
          source: 'MQTT',
        }

        setSensorReading({
          ...normalizedData,
          mqttConnected: true,
        })

      setDataSource('MQTT')
      setHasReceivedMqttData(true)

      const incidentLevel = getIncidentLevel(normalizedData)

      if (incidentLevel === 'normal') {
        setActiveIncident(null)
      } else {
        setActiveIncident((current) => {
          // Nếu cùng một cảnh báo vẫn đang diễn ra,
          // giữ nguyên thời điểm bắt đầu.
          if (current?.level === incidentLevel) {
            return current
          }

          return {
            level: incidentLevel,
            startedAt: normalizedData.timestamp,
          }
        })
      }

      const updatedHistory = recordSensorEvent(normalizedData)
      setEventHistory(updatedHistory)

      },
      (status) => {
        setMqttStatus(status)
      }
    )

    return () => {
      disconnectMqtt()
    }
  }, [])



  // Tạo và cập nhật dữ liệu mô phỏng mỗi 3 giây.
  useEffect(() => {
    // Không chạy mô phỏng khi MQTT đang kết nối hoặc đã kết nối.
    if (mqttStatus === 'connecting' || mqttStatus === 'connected') {
      return
    }

    // Nếu đã nhận dữ liệu MQTT trong phiên hiện tại,
    // không để dữ liệu mô phỏng ghi đè dữ liệu MQTT.
    if (hasReceivedMqttData) {
      return
    }

    const intervalId = setInterval(() => {
      const rawData = generateSensorData(scenario)
      const normalizedData = normalizeSensorData(rawData)

      setSensorReading(normalizedData)
      setDataSource('SIMULATOR')
    }, 3000)

    return () => clearInterval(intervalId)
  }, [scenario, hasReceivedMqttData, mqttStatus])



  // Lấy giá trị hiển thị cho từng thẻ cảm biến.

  const getSensorValue = (sensor) => {
    if (sensor.sensorType === 'eye') {
      if (dataSource === 'MQTT') {
        if (sensorReading.eyeStatus === 'normal') return 'Bình thường'
        if (sensorReading.eyeStatus === 'warning') return 'Dấu hiệu buồn ngủ'
        if (sensorReading.eyeStatus === 'critical') return 'Cảnh báo mức cao'
        return 'Chưa xác định'
      }
      return currentScenario.eye
    }

    if (sensor.sensorType === 'head') {
      if (dataSource === 'MQTT') {
        if (sensorReading.headStatus === 'normal') return 'Ổn định'
        if (sensorReading.headStatus === 'warning') return 'Cúi đầu bất thường'
        if (sensorReading.headStatus === 'critical') return 'Cảnh báo mức cao'
        return 'Chưa xác định'
      }
      return currentScenario.head
    }

    if (sensor.sensorType === 'heart') {
      return sensorReading.heartRate !== null
        ? `${sensorReading.heartRate} BPM`
        : '-- BPM'
    }

    if (sensor.sensorType === 'mqtt') {
      if (mqttStatus === 'connected') return 'Đã kết nối'
      if (mqttStatus === 'connecting') return 'Đang kết nối...'
      return 'Chưa kết nối'
    }

    return '--'
  }


  // Lấy trạng thái hiển thị cho từng thẻ cảm biến.
  const getSensorStatus = (sensor) => {
    if (sensor.sensorType === 'eye') {
      return dataSource === 'MQTT'
        ? sensorReading.eyeStatus
        : currentScenario.eyeStatus
    }

    if (sensor.sensorType === 'head') {
      return dataSource === 'MQTT'
        ? sensorReading.headStatus
        : currentScenario.headStatus
    }

    if (sensor.sensorType === 'mqtt') {
      if (mqttStatus === 'connected') return 'normal'
      if (mqttStatus === 'connecting') return 'unknown'
      return 'offline'
    }

    if (sensor.sensorType === 'heart') {
      return mqttStatus === 'connected' &&
        sensorReading.heartRate !== null
        ? 'normal'
        : 'offline'
    }

    return sensor.status
  }



  // Chuyển mã trạng thái thành nhãn tiếng Việt.
  const getStatusLabel = (status) => {
    if (status === 'normal') return 'BÌNH THƯỜNG'
    if (status === 'warning') return 'CẢNH BÁO'
    if (status === 'critical') return 'MỨC CAO'
    if (status === 'unknown') return 'CHƯA XÁC ĐỊNH'
    return 'OFFLINE'
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">D</div>

          <div>
            <strong>DriveGuard</strong>
            <span>INTELLIGENT MONITORING</span>
          </div>
        </div>

        <div className="menu-label">GIÁM SÁT</div>

        <nav className="navigation">
          <a className="nav-item active" href="#dashboard">
            <span>▦</span> Tổng quan
          </a>

          <a className="nav-item" href="#driver">
            <span>♙</span> Tài xế
          </a>

          <a className="nav-item" href="#sensors">
            <span>◉</span> Cảm biến
          </a>

          <a className="nav-item" href="#alerts">
            <span>⚠</span> Cảnh báo
          </a>

          <a className="nav-item" href="#history">
            <span>◷</span> Lịch sử sự kiện
          </a>
        </nav>

        <div className="menu-label system-label">HỆ THỐNG</div>

        <nav className="navigation">
          <a className="nav-item" href="#devices">
            <span>▣</span> Thiết bị IoT
          </a>

          <a className="nav-item" href="#settings">
            <span>⚙</span> Cấu hình
          </a>
        </nav>

        <div className="sidebar-bottom">
          <div className="connection-dot" />

          <div>
            <strong>Chế độ mô phỏng</strong>
            <span>Chưa kết nối phần cứng</span>
          </div>
        </div>
      </aside>

      <main className="main-content" id="dashboard">
        <header className="topbar">
          <div>
            <span className="breadcrumb">DriveGuard /</span>
            <strong> Tổng quan</strong>
          </div>



          <div className="topbar-right">
            <span className="demo-badge">
              {dataSource === 'MQTT' ? '● MQTT TEST' : '● DEMO MODE'}
            </span>

            <div className="account-info">
              <div className="account-avatar">{avatarText}</div>

              <div className="account-details">
                <strong className="account-name" title={username}>
                  {username}
                </strong>
                <span className="account-role">{roleLabel}</span>
              </div>

              <button
                type="button"
                className="logout-button"
                onClick={onLogout}
                title="Đăng xuất khỏi hệ thống"
                aria-label="Đăng xuất khỏi hệ thống"
              >
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M10 17l5-5-5-5" />
                  <path d="M15 12H3" />
                  <path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" />
                </svg>
                <span>Đăng xuất</span>
              </button>
            </div>
          </div>


        </header>

        <section className="dashboard-content">
          <div className="page-heading">
            <div>
              <div className="eyebrow">TRUNG TÂM ĐIỀU HÀNH IoT</div>
              <h1>Giám sát tài xế</h1>
              <p>
                Theo dõi trạng thái tài xế và thiết bị trong thời gian thực.
              </p>
            </div>

            <div className="simulation-tag">
              <span />
              {dataSource === 'MQTT'
                ? 'Dữ liệu từ MQTT'
                : 'Dữ liệu đang mô phỏng'}
            </div>
          </div>

          {/* Chọn tình huống mô phỏng */}
          <section className="scenario-panel">
            <div className="scenario-description">
              <h2>Mô phỏng tình huống</h2>
              <p>
                Chọn một kịch bản để kiểm tra phản hồi của giao diện.
              </p>
            </div>

            <div className="scenario-buttons">
              <button
                type="button"
                className={
                  scenario === 'normal'
                    ? 'scenario-button selected'
                    : 'scenario-button'
                }
                aria-pressed={scenario === 'normal'}
                onClick={() => setScenario('normal')}
              >
                Bình thường
              </button>

              <button
                type="button"
                className={
                  scenario === 'drowsy'
                    ? 'scenario-button selected'
                    : 'scenario-button'
                }
                aria-pressed={scenario === 'drowsy'}
                onClick={() => setScenario('drowsy')}
              >
                Mô phỏng ngủ gật
              </button>

              <button
                type="button"
                className={
                  scenario === 'unresponsive'
                    ? 'scenario-button selected'
                    : 'scenario-button'
                }
                aria-pressed={scenario === 'unresponsive'}
                onClick={() => setScenario('unresponsive')}
              >
                Không phản hồi
              </button>
            </div>
          </section>

          {/* Cảnh báo theo kịch bản */}

          <section
            className={`alert-banner alert-${activeAlert.alertLevel}`}
            id="alerts"
            aria-live="polite"
          >
            <div className="alert-symbol">
              {activeAlert.alertLevel === 'normal' ? '✓' : '!'}
            </div>

            <div className="alert-copy">
              <strong>{activeAlert.alertTitle}</strong>
              <p>{activeAlert.alertMessage}</p>
            </div>

            <span className="banner-status">
              {activeAlert.bannerStatus}
            </span>
          </section>


          {/* Thông tin cập nhật dữ liệu */}
          <div className="sensor-update-info" aria-live="off">
            <span>
              Nguồn: {dataSource === 'MQTT' ? 'MQTT' : 'Mô phỏng'}
            </span>

            <span>
              Cập nhật lúc:{' '}
              {new Date(sensorReading.timestamp).toLocaleTimeString('vi-VN', {
                timeZone: 'Asia/Ho_Chi_Minh',
                hour12: false,
              })}
            </span>

            <span>
              {dataSource === 'MQTT'
                ? 'Cập nhật theo dữ liệu MQTT'
                : 'Chu kỳ mô phỏng: 3 giây'}
            </span>
          </div>


          {dataSource === 'MQTT' && (
            <div className="sensor-update-info">
              <span>
                Trạng thái cảnh báo:{' '}
                {!activeIncident
                  ? 'Không có cảnh báo đang hoạt động'
                  : activeIncident.level === 'critical'
                    ? 'Đang có cảnh báo mức cao'
                    : activeIncident.level === 'warning'
                      ? 'Đang có cảnh báo ngủ gật'
                      : 'Đang chờ xác minh dữ liệu'}
              </span>

              {activeIncident?.startedAt && (
                <span>
                  Bắt đầu lúc:{' '}
                  {new Date(activeIncident.startedAt).toLocaleTimeString(
                    'vi-VN',
                    {
                      timeZone: 'Asia/Ho_Chi_Minh',
                      hour12: false,
                    }
                  )}
                </span>
              )}
            </div>
          )}


          
          <section className="panel simulation-details">

          <div className="panel-heading">
            <div>
              <h2>
                {dataSource === 'MQTT'
                  ? 'Thông số cảm biến MQTT'
                  : 'Thông số cảm biến mô phỏng'}
              </h2>
              <p>
                {dataSource === 'MQTT'
                  ? 'Các giá trị được cập nhật từ thông điệp MQTT'
                  : 'Các giá trị thay đổi theo kịch bản đã chọn'}
              </p>
            </div>

            <span className="live-label">
              {dataSource === 'MQTT' ? 'MQTT' : 'DEMO'}
            </span>
          </div>


            <div className="simulation-details-grid">
              <div className="simulation-detail-card">
                <span>Thời gian nhắm mắt</span>
                <strong>
                  {sensorReading.eyeClosureDurationMs === null
                    ? 'Không xác định'
                    : `${sensorReading.eyeClosureDurationMs} ms`}
                </strong>
              </div>

              <div className="simulation-detail-card">
                <span>Góc cúi đầu</span>
                <strong>
                  {sensorReading.headTiltDegrees}°
                </strong>
              </div>

              <div className="simulation-detail-card">
                <span>Nhịp tim</span>
                <strong>
                  {sensorReading.heartRate === null
                    ? 'Chưa có dữ liệu'
                    : `${sensorReading.heartRate} BPM`}
                </strong>
              </div>
            </div>
          </section>


          {/* Các thẻ cảm biến */}
          <section className="metrics-grid" id="sensors">
            {sensorData.map((sensor) => {
              const status = getSensorStatus(sensor)

              return (
                <article className="metric-card" key={sensor.label}>
                  <div className="metric-top">
                    <span className="metric-icon">{sensor.icon}</span>

                    <span className={`status-pill ${status}`}>
                      {getStatusLabel(status)}
                    </span>
                  </div>

                  <div className="metric-label">{sensor.label}</div>

                  <div className="metric-value">
                    {getSensorValue(sensor)}
                  </div>


                  <div className="metric-detail">
                    {sensor.sensorType === 'mqtt'
                      ? (mqttStatus === 'connected'
                          ? 'Đã kết nối broker MQTT'
                          : mqttStatus === 'connecting'
                            ? 'Đang kết nối broker MQTT'
                            : 'Mất kết nối broker MQTT')

                      : sensor.sensorType === 'heart'
                        ? (sensorReading.heartRate === null
                            ? 'Chưa nhận dữ liệu cảm biến'
                            : dataSource === 'MQTT'
                              ? 'Dữ liệu nhận qua MQTT'
                              : 'Dữ liệu mô phỏng')

                        : dataSource === 'MQTT'
                          ? 'Dữ liệu nhận qua MQTT'
                          : sensor.detail}
                  </div>

                </article>
              )
            })}
          </section>

          {/* Thông tin tài xế */}
          <section className="content-grid">
            <article className="panel driver-panel" id="driver">
              <div className="panel-heading">
                <div>
                  <h2>Thông tin tài xế</h2>
                  <p>Hồ sơ giám sát hiện tại</p>
                </div>


                <span className="live-label">
                  <i />
                  {dataSource === 'MQTT' ? 'MQTT TEST' : 'DEMO'}
                </span>

              </div>

              <div className="driver-profile">
                <div className="driver-avatar">TX</div>

                <div>
                  <h3>Tài xế mô phỏng</h3>
                  <p>Mã tài xế: DRIVER-DEMO-001</p>
                  <span className="driver-state">
                    ● Chưa có dữ liệu nhận dạng
                  </span>
                </div>
              </div>

              <div className="detail-list">
                <div>
                  <span>Thiết bị giám sát</span>
                  <strong>ESP32-S3-CAM (mô phỏng)</strong>
                </div>

                <div>
                  <span>Radar mmWave</span>
                  <strong>Chưa kết nối</strong>
                </div>

                <div>
                  <span>GPS / LTE</span>
                  <strong>Chưa kết nối</strong>
                </div>
              </div>
            </article>

            
            {/* Sự kiện gần đây */}
            <article className="panel event-panel" id="history">

            <div className="panel-heading">
              <div>
                <h2>Sự kiện gần đây</h2>
                <p>Lịch sử sự kiện nhận từ MQTT</p>
              </div>

              <span className="event-count">
                {filteredEventHistory.length}
              </span>
            </div>

            <div className="event-filter">
              <label htmlFor="eventFilter">Lọc sự kiện:</label>

              <select
                id="eventFilter"
                value={eventFilter}
                onChange={(e) => setEventFilter(e.target.value)}
              >
                <option value="all">Tất cả</option>
                <option value="warning">Cảnh báo ngủ gật</option>
                <option value="critical">Cảnh báo mức cao</option>
                <option value="unknown">Chưa xác định</option>
              </select>
            </div>


              {filteredEventHistory.length === 0 ? (
                <div className="event-note">
                  Chưa có sự kiện MQTT. Hãy gửi dữ liệu cảm biến để bắt đầu ghi nhận.
                </div>
              ) : (
                filteredEventHistory.map((event) => (
                  // Hiển thị từng sự kiện
                  <div className="event-item" key={event.id}>
                    <div
                      className={`event-icon ${
                        event.level === 'critical'
                          ? 'critical'
                          : event.level === 'warning'
                            ? 'warning'
                            : event.level === 'unknown'
                              ? 'info'
                              : 'success'
                      }`}
                    >
                      {event.level === 'normal' ? '✓' : '!'}
                    </div>


                    <div className="event-copy">
                      <strong>{event.title}</strong>

                      <p>{event.message}</p>

                      <div className="event-lifecycle">
                        <span
                          className={`event-lifecycle-status ${
                            event.status === 'ACTIVE' ? 'active' : 'resolved'
                          }`}
                        >
                          {event.status === 'ACTIVE'
                            ? 'Đang diễn ra'
                            : 'Đã kết thúc'}
                        </span>
                      </div>

                      <div className="event-time-details">
                        <span>
                          Bắt đầu:{' '}
                          {new Date(event.startedAt || event.timestamp).toLocaleString(
                            'vi-VN',
                            {
                              timeZone: 'Asia/Ho_Chi_Minh',
                              hour12: false,
                            }
                          )}
                        </span>

                        {event.endedAt && (
                          <span>
                            Kết thúc:{' '}
                            {new Date(event.endedAt).toLocaleString('vi-VN', {
                              timeZone: 'Asia/Ho_Chi_Minh',
                              hour12: false,
                            })}
                          </span>
                        )}

                        {event.durationMs !== null &&
                          event.durationMs !== undefined && (
                            <span>
                              Thời lượng:{' '}
                              {event.durationMs < 1000
                                ? `${event.durationMs} ms`
                                : event.durationMs < 60000
                                  ? `${(event.durationMs / 1000).toFixed(1)} giây`
                                  : `${Math.floor(event.durationMs / 60000)} phút ${Math.floor(
                                      (event.durationMs % 60000) / 1000
                                    )} giây`}
                            </span>
                          )}
                      </div>

                      <span>
                        {new Date(event.timestamp).toLocaleString('vi-VN', {
                          timeZone: 'Asia/Ho_Chi_Minh',
                          hour12: false,
                        })}
                        {' · Nguồn: '}
                        {event.source}
                      </span>
                    </div>

                  </div>
                ))
              )}
            </article>


          </section>

          <footer className="footer">
            <span>DriveGuard · Hệ thống IoT giám sát tài xế</span>
            <span>Giai đoạn 1: Frontend · React + Vite</span>
          </footer>
        </section>
      </main>
    </div>
  )
}

export default App
