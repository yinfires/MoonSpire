from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image


def red_outline_bbox(reference: Image.Image) -> tuple[int, int, int, int]:
    arr = np.array(reference.convert("RGBA"))
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]
    yy, xx = np.indices(arr.shape[:2])
    red = (a > 128) & (r > 210) & (g < 70) & (b < 70) & (xx < 100) & (yy < 90)
    ys, xs = np.where(red)
    if len(xs) == 0:
        return (15, 3, 82, 58)
    pad = 2
    return (
        max(0, int(xs.min()) - pad),
        max(0, int(ys.min()) - pad),
        min(arr.shape[1], int(xs.max()) + pad + 1),
        min(arr.shape[0], int(ys.max()) + pad + 1),
    )


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    out = mask.copy()
    h, w = mask.shape
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dx * dx + dy * dy > radius * radius:
                continue
            sy0 = max(0, -dy)
            sy1 = min(h, h - dy)
            sx0 = max(0, -dx)
            sx1 = min(w, w - dx)
            dy0 = max(0, dy)
            dy1 = min(h, h + dy)
            dx0 = max(0, dx)
            dx1 = min(w, w + dx)
            out[dy0:dy1, dx0:dx1] |= mask[sy0:sy1, sx0:sx1]
    return out


def inpaint_average(arr: np.ndarray, mask: np.ndarray, iterations: int = 90) -> np.ndarray:
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


def remove_digit_from_badge(original: Image.Image, badge_box: tuple[int, int, int, int]) -> Image.Image:
    arr = np.array(original.convert("RGBA"))
    x0, y0, x1, y1 = badge_box
    yy, xx = np.indices(arr.shape[:2])
    # Only target the digit and its shadow inside the locked badge. The badge's
    # outer geometry and placement come from the original image unchanged.
    cx = x0 + (x1 - x0) * 0.52
    cy = y0 + (y1 - y0) * 0.51
    digit_zone = (((xx - cx) / 17.0) ** 2 + ((yy - cy) / 24.0) ** 2 < 1.0)
    digit_zone &= (xx >= x0 + 16) & (xx <= x1 - 15) & (yy >= y0 + 9) & (yy <= y1 - 8)
    rgb = arr[:, :, :3]
    mx = rgb.max(axis=2)
    mn = rgb.min(axis=2)
    bright_digit = (mx > 120) & (mn > 62) & ((mx - mn) < 125)
    dark_shadow = (mx < 92) & (mn < 62)
    digit = digit_zone & (bright_digit | dark_shadow)
    digit = dilate(digit, 4)
    digit &= digit_zone

    arr = inpaint_average(arr, digit)

    return Image.fromarray(arr, "RGBA")


def main() -> None:
    original_path = Path(r"D:\图片\素材\360px-Snakebite.png")
    reference_path = Path(r"D:\图片\素材\360px-Snakebite - 副本.png")
    generated_path = Path(
        r"C:\Users\yinfire\.codex\generated_images\019df6b7-f17e-7491-b312-f75b991e9502"
        r"\ig_0875dea4a001fc2a0169f9908cfce08196bdfd856448769599.png"
    )
    output_path = original_path.with_name("360px-Snakebite_generated_locked_energy.png")

    original = Image.open(original_path).convert("RGBA")
    generated = Image.open(generated_path).convert("RGBA").resize(original.size, Image.Resampling.LANCZOS)
    reference = Image.open(reference_path).convert("RGBA")
    badge_box = red_outline_bbox(reference)

    locked_original = remove_digit_from_badge(original, badge_box)
    badge = locked_original.crop(badge_box)
    generated.alpha_composite(badge, badge_box[:2])
    generated.save(output_path)
    print(output_path)
    print(badge_box)


if __name__ == "__main__":
    main()
