from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image


def local_mask(arr: np.ndarray, box: tuple[int, int, int, int], predicate) -> np.ndarray:
    x0, y0, x1, y1 = box
    rgb = arr[y0:y1, x0:x1, :3]
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    mx = rgb.max(axis=2)
    mn = rgb.min(axis=2)
    out = np.zeros(arr.shape[:2], dtype=bool)
    out[y0:y1, x0:x1] = predicate(r, g, b, mx, mn)
    return out


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    out = mask.copy()
    h, w = mask.shape
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx * dx + dy * dy > radius * radius:
                continue
            src_y0 = max(0, -dy)
            src_y1 = min(h, h - dy)
            src_x0 = max(0, -dx)
            src_x1 = min(w, w - dx)
            dst_y0 = max(0, dy)
            dst_y1 = min(h, h + dy)
            dst_x0 = max(0, dx)
            dst_x1 = min(w, w + dx)
            out[dst_y0:dst_y1, dst_x0:dst_x1] |= mask[src_y0:src_y1, src_x0:src_x1]
    return out


def inpaint_nearest_average(arr: np.ndarray, mask: np.ndarray, iterations: int = 120) -> np.ndarray:
    rgb = arr[:, :, :3].astype(np.float32)
    alpha = arr[:, :, 3:4].astype(np.float32)
    filled = ~mask

    for _ in range(iterations):
        if filled.all():
            break
        sum_rgb = np.zeros_like(rgb)
        sum_alpha = np.zeros_like(alpha)
        count = np.zeros(mask.shape, dtype=np.float32)

        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)):
            sy0 = max(0, -dy)
            sy1 = min(mask.shape[0], mask.shape[0] - dy)
            sx0 = max(0, -dx)
            sx1 = min(mask.shape[1], mask.shape[1] - dx)
            dy0 = max(0, dy)
            dy1 = min(mask.shape[0], mask.shape[0] + dy)
            dx0 = max(0, dx)
            dx1 = min(mask.shape[1], mask.shape[1] + dx)
            valid = filled[sy0:sy1, sx0:sx1]
            sum_rgb[dy0:dy1, dx0:dx1] += rgb[sy0:sy1, sx0:sx1] * valid[:, :, None]
            sum_alpha[dy0:dy1, dx0:dx1] += alpha[sy0:sy1, sx0:sx1] * valid[:, :, None]
            count[dy0:dy1, dx0:dx1] += valid

        frontier = mask & ~filled & (count > 0)
        rgb[frontier] = sum_rgb[frontier] / count[frontier, None]
        alpha[frontier] = sum_alpha[frontier] / count[frontier, None]
        filled[frontier] = True

    out = arr.copy()
    out[:, :, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[:, :, 3:4] = np.clip(alpha, 0, 255).astype(np.uint8)
    return out


def fill_from_region(arr: np.ndarray, mask: np.ndarray, source_box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = source_box
    source = arr[y0:y1, x0:x1, :3]
    colors = source.reshape(-1, 3)
    if len(colors) == 0:
        return
    mean = np.median(colors, axis=0).astype(np.uint8)
    arr[mask, :3] = mean


def fill_gradient_x(arr: np.ndarray, mask: np.ndarray, box: tuple[int, int, int, int], left_x: int, right_x: int) -> None:
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        left = arr[y, left_x, :3].astype(np.float32)
        right = arr[y, right_x, :3].astype(np.float32)
        for x in range(x0, x1):
            if not mask[y, x]:
                continue
            t = (x - x0) / max(1, x1 - x0 - 1)
            arr[y, x, :3] = np.clip(left * (1 - t) + right * t, 0, 255).astype(np.uint8)


def fill_gradient_y(arr: np.ndarray, mask: np.ndarray, box: tuple[int, int, int, int], top_y: int, bottom_y: int) -> None:
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        for x in range(x0, x1):
            if not mask[y, x]:
                continue
            top = arr[top_y, x, :3].astype(np.float32)
            bottom = arr[bottom_y, x, :3].astype(np.float32)
            arr[y, x, :3] = np.clip(top * (1 - t) + bottom * t, 0, 255).astype(np.uint8)


def build_text_mask(arr: np.ndarray) -> np.ndarray:
    mask = np.zeros(arr.shape[:2], dtype=bool)

    # Cost number only. The green gem itself is never masked as a whole.
    cost_region = np.zeros(arr.shape[:2], dtype=bool)
    cost_region[18:46, 32:57] = True
    yy, xx = np.indices(mask.shape)
    cost_digit_area = cost_region & (((xx - 44) / 9.5) ** 2 + ((yy - 32) / 14.5) ** 2 < 1.0)
    mask |= cost_digit_area

    # Title and type label: pale glyphs plus their darker shadow pixels.
    white_text = lambda r, g, b, mx, mn: (mx > 130) & (mn > 65) & ((mx - mn) < 115)
    shadow_text = lambda r, g, b, mx, mn: (mx < 105) & (mn < 75)
    mask |= local_mask(arr, (150, 34, 210, 64), white_text)
    mask |= local_mask(arr, (150, 34, 210, 64), shadow_text)
    mask |= local_mask(arr, (160, 232, 199, 251), white_text)
    mask |= local_mask(arr, (160, 232, 199, 251), shadow_text)

    # Rules text: yellow/white glyphs and subtle shadows, restricted to text lines.
    rules = lambda r, g, b, mx, mn: (
        ((r > 132) & (g > 115) & (b < 92))
        | ((mx > 150) & (mn > 82) & ((mx - mn) < 105))
        | ((mx < 83) & (mn < 62))
    )
    mask |= local_mask(arr, (116, 297, 236, 324), rules)
    mask |= local_mask(arr, (80, 324, 282, 354), rules)
    mask |= local_mask(
        arr,
        (80, 286, 282, 360),
        lambda r, g, b, mx, mn: (mx > 86) & (mx < 155) & (mn > 48) & ((mx - mn) > 20),
    )

    return dilate(mask, 1)


def repair_text_pixels(arr: np.ndarray, mask: np.ndarray) -> np.ndarray:
    out = arr.copy()

    cost = np.zeros(mask.shape, dtype=bool)
    cost[18:46, 32:57] = mask[18:46, 32:57]
    fill_gradient_x(out, cost, (32, 18, 57, 46), 30, 59)

    title = np.zeros(mask.shape, dtype=bool)
    title[32:66, 145:215] = mask[32:66, 145:215]
    fill_gradient_x(out, title, (145, 32, 215, 66), 137, 225)

    type_label = np.zeros(mask.shape, dtype=bool)
    type_label[229:253, 156:204] = mask[229:253, 156:204]
    fill_from_region(out, type_label, (157, 229, 204, 253))

    rules = np.zeros(mask.shape, dtype=bool)
    rules[292:358, 76:285] = mask[292:358, 76:285]
    fill_from_region(out, rules, (96, 274, 266, 292))

    return out


def save_mask_preview(img: Image.Image, mask: np.ndarray, path: Path) -> None:
    arr = np.array(img.convert("RGBA"))
    overlay = arr.copy()
    overlay[mask, :3] = (255, 0, 0)
    overlay[mask, 3] = 255
    Image.fromarray(overlay, "RGBA").save(path)


def main() -> None:
    src = Path(r"D:\图片\素材\360px-Snakebite.png")
    dst = src.with_name("360px-Snakebite_no_text.png")
    preview = src.with_name("360px-Snakebite_text_mask_preview.png")
    img = Image.open(src).convert("RGBA")
    arr = np.array(img)
    mask = build_text_mask(arr)
    save_mask_preview(img, mask, preview)
    out = repair_text_pixels(arr, mask)
    Image.fromarray(out, "RGBA").save(dst)
    print(dst)
    print(preview)


if __name__ == "__main__":
    main()
