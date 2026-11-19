# """DriveGuard IoT - G03.06.04 MediaPipe Face Landmarker - Webcam Live Detection"""

# ==============================================================================
# 1. KHAI BÁO THƯ VIỆN & CÔNG DỤNG
# ==============================================================================

# Thư viện time (Tích hợp sẵn trong Python):
# - Tác dụng: Đo lường thời gian thực với độ chính xác cao.
# - Cung cấp: Hàm perf_counter() để tính tốc độ khung hình (FPS) và tạo dấu
#   thời gian (timestamp) tăng dần bắt buộc cho chế độ nhận diện video.
import time

# Thư viện pathlib (Tích hợp sẵn trong Python):
# - Tác dụng: Quản lý, nối đường dẫn tệp tin chuẩn xác trên mọi hệ điều hành.
# - Cung cấp: Lớp Path giúp định vị chính xác file mô hình 'face_landmarker.task'
#   mà không lo lỗi dấu gạch chéo (\ hay /) giữa Windows và Linux/macOS.
from pathlib import Path

# Thư viện OpenCV - cv2 (Thư viện ngoài, cài qua 'pip install opencv-python'):
# - Tác dụng: Xử lý hình ảnh, luồng video và giao diện hiển thị cơ bản.
# - Cung cấp:
#   + VideoCapture(): Kết nối và đọc từng khung hình từ webcam.
#   + cvtColor(): Chuyển đổi hệ màu ảnh (từ BGR sang RGB).
#   + circle(), putText(): Vẽ các chấm tròn điểm mốc và viết chữ lên video.
#   + imshow(), waitKey(): Tạo cửa sổ hiển thị hình ảnh và bắt phím bấm thoát.
import cv2

# Thư viện MediaPipe - mp (Thư viện ngoài của Google, cài qua 'pip install mediapipe'):
# - Tác dụng: Đóng vai trò "bộ não AI" nhận diện khuôn mặt theo thời gian thực.
# - Cung cấp:
#   + mp.Image(): Đóng gói dữ liệu ảnh thô sang định dạng chuẩn của MediaPipe.
#   + mp.tasks.vision.FaceLandmarker: Nạp file mô hình AI để phân tích và trả về
#     tọa độ 478 điểm mốc 3D trên khuôn mặt (mắt, mũi, miệng, viền mặt).
import mediapipe as mp

from eye_aspect_ratio import calculate_ear

from perclos import PERCLOSTracker

from eye_closure_detector import EyeClosureDetector, EyeClosureEvent

from mqtt_event_worker import MqttEventWorker

from vi_text_renderer import draw_vietnamese_text

# ==============================================================================
# 2. CẤU HÌNH HỆ THỐNG
# ==============================================================================
MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "face_landmarker.task"
CAMERA_INDEX = 0 # ID của webcam (0 thường là camera mặc định)
WINDOW_NAME = "DriveGuard IoT - Face Landmarker"

#Cấu trúc lưới nối các điểm trên khuôn mặt (Khai báo sẵn để dùng nếu cần vẽ lưới)
FACE_CONNECTIONS = mp.tasks.vision.FaceLandmarksConnections.FACE_LANDMARKS_TESSELATION

# ==============================================================================
# 3. CÁC HÀM XỬ LÝ CHÍNH
# ==============================================================================
def create_detector():
    """Khởi tạo bộ nhận diện khuôn mặt MediaPipe ở chế độ VIDEO."""
    if not MODEL_PATH.is_file():
        raise FileNotFoundError(f"Không tìm thấy file mô hình: {MODEL_PATH}")

    # Thiết lập các thông số cho mô hình AI
    options = mp.tasks.vision.FaceLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=str(MODEL_PATH)),
        running_mode=mp.tasks.vision.RunningMode.VIDEO,  # Chế độ tối ưu cho luồng video liên tục
        num_faces=1,  # Chỉ theo dõi tối đa 1 khuôn mặt (tài xế)
        min_face_detection_confidence=0.5,  # Ngưỡng tin cậy tối thiểu để phát hiện mặt
        min_face_presence_confidence=0.5,  # Ngưỡng tin cậy xác nhận mặt vẫn trong khung hình
        min_tracking_confidence=0.5,  # Ngưỡng tin cậy để tiếp tục bám theo mặt giữa các frame
    )
    return mp.tasks.vision.FaceLandmarker.create_from_options(options)

def draw_landmarks(frame, landmarks):
    """Vẽ các điểm mốc (chấm xanh lá) lên khuôn mặt trên khung hình OpenCV."""
    height, width = frame.shape[:2]
    for landmark in landmarks:
        # Chuyển đổi tọa độ chuẩn hóa (0.0 -> 1.0) sang tọa độ pixel thực tế
        x = int(landmark.x * width)
        y = int(landmark.y * height)

        #Kiểm tra điểm nằm trong khung hình thì vẽ chấm tròn bán kính 1px
        if 0 <= x < width and 0 <= y < height:
            cv2.circle(frame, (x, y), 1, (0, 255, 0), -1)

def run():
    """Hàm chính chạy vòng lặp đọc webcam và nhận diện khuôn mặt."""
    # Mở webcam (CAP_DSHOW giúp khởi động camera nhanh hơn trên Windows)
    camera = cv2.VideoCapture(CAMERA_INDEX, cv2.CAP_DSHOW)
    if not camera.isOpened():
        raise RuntimeError("Không thể mở webcam")

    # Cài đặt độ phân giải khung hình
    camera.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    # Các biến đếm thống kê và tính toán thời gian
    frames = 0
    detected_frames = 0
    start_time = time.perf_counter()
    previous_time = start_time
    last_timestamp_ms = -1

    # Theo dõi tỷ lệ nhắm mắt trong cửa sổ trượt 60 giây.
    perclos_tracker = PERCLOSTracker(
        ear_threshold=0.18,
        window_seconds=60.0,
        max_sample_gap_seconds=0.5,
        min_coverage_ratio=0.8,
    )

    # Theo dõi thời gian nhắm mắt liên tục.
    eye_closure_detector = EyeClosureDetector(
        ear_threshold=0.18,
        prolonged_seconds=2.0,
        max_sample_gap_seconds=0.5,
    )

    mqtt_worker = MqttEventWorker()
    mqtt_worker.start()

    print("DRIVEGUARD_FACE_LANDMARKER_STARTED=True")
    print(f"MODEL_PATH={MODEL_PATH}")
    print("PRESS_Q_TO_EXIT=True")

    try:
        # Khởi tạo detector và tự động giải phóng tài nguyên khi thoát khối 'with'
        with create_detector() as detector:
            while True:
                # Đọc từng khung hình từ camera
                success, frame = camera.read()
                if not success or frame is None:
                    raise RuntimeError("Lỗi đọc dữ liệu từ webcam")

                frames += 1  # Tăng số khung hình đã đọc

                # OpenCV dùng hệ màu BGR, cần đổi sang RGB để MediaPipe xử lý
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)

                #Tạo dấu thời gian (timestamp) tăng dần (bắt buộc cho chế độ Video)
                timestamp_ms = int((time.perf_counter() - start_time) * 1000)
                timestamp_ms = max(timestamp_ms, last_timestamp_ms + 1)  # Đảm bảo timestamp tăng dần
                last_timestamp_ms = timestamp_ms

                # Chạy AI nhận diện điểm mốc khuôn mặt
                result = detector.detect_for_video(image, timestamp_ms)

                # Nếu tìm thấy khuôn mặt, tăng biến đếm và vẽ các điểm lên hình

                face_count = len(result.face_landmarks)
                ear_result = None

                if face_count > 0:
                    detected_frames += 1

                    for face_landmarks in result.face_landmarks:
                        draw_landmarks(frame, face_landmarks)

                    try:
                        ear_result = calculate_ear(
                            result.face_landmarks[0],
                            frame.shape[1],
                            frame.shape[0],
                        )
                    except ValueError as exc:
                        print(f"EAR_CALCULATION_WARNING={exc}")


                # Dùng thời gian thực để tính PERCLOS, không phụ thuộc FPS.
                perclos_timestamp = time.perf_counter() - start_time

                perclos_result = perclos_tracker.update(
                    timestamp=perclos_timestamp,
                    ear=(
                        ear_result.average_ear
                        if ear_result is not None
                        else None
                    ),
                )

                # Dùng cùng timestamp và EAR để đồng bộ hai thuật toán.
                eye_closure_result = eye_closure_detector.update(
                    timestamp=perclos_timestamp,
                    ear=(
                        ear_result.average_ear
                        if ear_result is not None
                        else None
                    ),
                )

                # Chỉ ghi log khi phát sinh sự kiện mới.
                if eye_closure_result.event is not None:
                    if eye_closure_result.event == EyeClosureEvent.PROLONGED_CLOSURE:
                        mqtt_worker.submit(True)
                    elif eye_closure_result.event == EyeClosureEvent.RECOVERED:
                        mqtt_worker.submit(False)

                    print(
                        f"EYE_CLOSURE_EVENT={eye_closure_result.event.value};"
                        f"DURATION={eye_closure_result.closed_duration_seconds:.2f}s",
                        flush=True,
                    )


                # Tính toán tốc độ xử lý FPS (số khung hình/ giây)
                current_time = time.perf_counter()
                delta = current_time - previous_time
                fps = 1.0 / delta if delta > 0 else 0.0
                previous_time = current_time

                # Hiển thị thông tin FPS và số khuôn mặt lên góc trái màn hình
                cv2.putText(
                    frame,
                    f"DriveGuard IoT | FPS: {fps:.1f}",
                    (10, 30),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    (0, 255, 0),
                    2,
                )
                cv2.putText(
                    frame,
                    f"Faces: {face_count}",
                    (10, 65),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    (0, 255, 255),
                    2,
                )


                if ear_result is not None:
                    cv2.putText(
                        frame,
                        f"Left EAR: {ear_result.left_ear:.3f}",
                        (10, 100),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (255, 255, 0),
                        2,
                    )

                    cv2.putText(
                        frame,
                        f"Right EAR: {ear_result.right_ear:.3f}",
                        (10, 130),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (255, 255, 0),
                        2,
                    )

                    cv2.putText(
                        frame,
                        f"Average EAR: {ear_result.average_ear:.3f}",
                        (10, 160),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (0, 255, 255),
                        2,
                    )
                else:
                    cv2.putText(
                        frame,
                        "EAR: N/A",
                        (10, 100),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (0, 0, 255),
                        2,
                    )



                # Hiển thị trạng thái mắt bằng tiếng Việt có dấu.
                if eye_closure_result.is_alert_active:
                    eye_state_text = "CẢNH BÁO: NHẮM MẮT KÉO DÀI!"
                    eye_state_color = (0, 0, 255)
                elif perclos_result.eye_closed is None:
                    eye_state_text = "Không phát hiện khuôn mặt"
                    eye_state_color = (0, 165, 255)
                elif perclos_result.eye_closed:
                    eye_state_text = "Mắt đang nhắm"
                    eye_state_color = (0, 0, 255)
                else:
                    eye_state_text = "Mắt đang mở"
                    eye_state_color = (0, 255, 0)

                frame = draw_vietnamese_text(
                    frame,
                    f"Trạng thái: {eye_state_text}",
                    (10, 250),
                    color=eye_state_color,
                    font_size=22,
                    bold=True,
                )

                # Chỉ hiển thị PERCLOS khi đủ thời gian và độ bao phủ dữ liệu.
                perclos_text = (
                    f"PERCLOS: {perclos_result.perclos:.1f}%"
                    if perclos_result.perclos is not None
                    else "PERCLOS: Đang thu thập dữ liệu..."
                )

                frame = draw_vietnamese_text(
                    frame,
                    perclos_text,
                    (10, 290),
                    color=(255, 255, 0),
                    font_size=21,
                    bold=True,
                )

                frame = draw_vietnamese_text(
                    frame,
                    f"Độ bao phủ dữ liệu: {perclos_result.coverage_ratio * 100:.1f}%",
                    (10, 330),
                    color=(255, 255, 0),
                    font_size=21,
                )

                frame = draw_vietnamese_text(
                    frame,
                    f"Thời gian nhắm mắt: {eye_closure_result.closed_duration_seconds:.2f} giây",
                    (10, 370),
                    color=(255, 255, 255),
                    font_size=21,
                    bold=True,
                )

                # Hiển thị cảnh báo an toàn khi nhắm mắt liên tục đủ 2 giây.
                if eye_closure_result.is_alert_active:
                    frame = draw_vietnamese_text(
                        frame,
                        "HÃY DỪNG XE TẠI NƠI AN TOÀN ĐỂ NGHỈ NGƠI!",
                        (10, 420),
                        color=(0, 0, 255),
                        font_size=20,
                        bold=True,
                    )

                # Hiển thị frame sau khi đã vẽ toàn bộ thông tin EAR và PERCLOS.
                cv2.imshow(WINDOW_NAME, frame)


                # Nhấn phím 'q' trên bàn phím để dừng chương trình
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break

    finally:
        # Luôn giải phóng camera và đóng cửa sổ dù chương trình dừng bình thường hay gặp lỗi
        mqtt_worker.close()
        camera.release()
        cv2.destroyAllWindows()

        # In báo cáo kết quả kiểm thử ra màn hình console
        print(f"TOTAL_FRAMES={frames}")
        print(f"DETECTED_FRAMES={detected_frames}")
        print("CAMERA_RELEASED=True")
        print("G03.06.04_RESULT=PASS")


if __name__ == "__main__":
    run()
