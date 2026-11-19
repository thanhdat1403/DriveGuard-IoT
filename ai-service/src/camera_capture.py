import time
import cv2


def main():
    camera = cv2.VideoCapture(0, cv2.CAP_DSHOW)

    if not camera.isOpened():
        print("ERROR: Cannot open camera")
        return 1

    previous_time = time.perf_counter()
    frames = 0

    try:
        while True:
            success, frame = camera.read()

            if not success or frame is None:
                print("ERROR: Cannot read camera frame")
                return 1

            frames += 1
            current_time = time.perf_counter()
            elapsed = current_time - previous_time
            fps = 1.0 / elapsed if elapsed > 0 else 0.0
            previous_time = current_time

            cv2.putText(
                frame,
                f"DriveGuard AI - FPS: {fps:.1f}",
                (15, 35),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                (0, 255, 0),
                2,
            )

            cv2.imshow("DriveGuard AI - Camera", frame)

            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    finally:
        camera.release()
        cv2.destroyAllWindows()

    print(f"TOTAL_FRAMES={frames}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
