#!/usr/bin/env python3
"""يولّد أيقونة تشغيل NEXA Code 2100 بكل الكثافات: زجاج غامق + توهج نيون + </>."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
S = 512  # لوحة الرسم الأساسية


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (size, size), top)
    px = img.load()
    for y in range(size):
        f = y / size
        c = tuple(int(top[i] + (bottom[i] - top[i]) * f) for i in range(3))
        for x in range(size):
            px[x, y] = c
    return img


def make_icon(size):
    img = vertical_gradient(S, (7, 7, 28), (13, 10, 44)).convert("RGBA")
    # توهجات نيون ضبابية (إحساس السائل/المية)
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([20, 10, 300, 240], fill=(0, 229, 255, 120))     # سماوي
    gd.ellipse([230, 200, 500, 470], fill=(139, 92, 255, 120))  # بنفسجي
    gd.ellipse([150, 330, 380, 510], fill=(255, 62, 165, 90))   # وردي
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    # إطار نيون مزدوج
    d.rounded_rectangle([14, 14, S - 14, S - 14], radius=96, outline=(0, 229, 255, 235), width=10)
    d.rounded_rectangle([30, 30, S - 30, S - 30], radius=82, outline=(139, 92, 255, 160), width=5)
    # رمز الكود </> في المنتصف
    font = ImageFont.truetype(FONT, 208)
    d.text((S / 2, S / 2 - 14), "</>", font=font, anchor="mm", fill=(240, 253, 255, 255))
    # نقطة مستقبلية صغيرة تحت الرمز
    d.ellipse([S / 2 - 13, S - 118, S / 2 + 13, S - 92], fill=(0, 255, 200, 255))
    if size != S:
        img = img.resize((size, size), Image.LANCZOS)
    return img


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in DENSITIES.items():
    folder = os.path.join(BASE, f"mipmap-{name}")
    os.makedirs(folder, exist_ok=True)
    make_icon(px).save(os.path.join(folder, "ic_launcher.png"))
    print("wrote", folder, px)
print("icons done")
