
"""Hiển thị văn bản tiếng Việt có dấu trên khung hình OpenCV."""

from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont


FONT_DIR = Path("C:/Windows/Fonts")


@lru_cache(maxsize=32)
def load_font(size: int, bold: bool = False):
    """Nạp font một lần để giảm chi phí xử lý mỗi frame."""
    if size <= 0:
        raise ValueError("Kích thước font phải lớn hơn 0.")

    font_name = "arialbd.ttf" if bold else "arial.ttf"
    font_path = FONT_DIR / font_name

    if not font_path.is_file():
        raise FileNotFoundError(f"Không tìm thấy font: {font_path}")

    return ImageFont.truetype(str(font_path), size)


def draw_vietnamese_text(
    frame: np.ndarray,
    text: str,
    position: tuple[int, int],
    color: tuple[int, int, int] = (255, 255, 255),
    font_size: int = 24,
    bold: bool = False,
) -> np.ndarray:
    """
    Vẽ tiếng Việt có dấu lên ảnh OpenCV.

    color sử dụng định dạng BGR giống OpenCV.
    """
    if frame is None or frame.size == 0:
        raise ValueError("Frame không hợp lệ.")

    if not isinstance(text, str):
        raise TypeError("Nội dung hiển thị phải là chuỗi.")

    if frame.ndim != 3 or frame.shape[2] != 3:
        raise ValueError("Frame phải có 3 kênh màu BGR.")

    font = load_font(font_size, bold)

    # OpenCV dùng BGR, Pillow dùng RGB.
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    image = Image.fromarray(rgb_frame)

    draw = ImageDraw.Draw(image)

    # Đảo thứ tự kênh màu khi chuyển từ BGR sang RGB.
    rgb_color = (color[2], color[1], color[0])

    draw.text(
        position,
        text,
        font=font,
        fill=rgb_color,
    )

    return cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)
