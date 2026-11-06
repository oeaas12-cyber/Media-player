"""Generates the abstract artwork bundled with the demo library (res/drawable-nodpi)."""
import math, random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
BOLD = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
MED = "/usr/share/fonts/opentype/inter/InterDisplay-Medium.otf"

TITLES = {
    "bbb": ("Big Buck Bunny", [(46, 125, 50), (174, 213, 129), (255, 213, 79)]),
    "elephants": ("Elephants Dream", [(62, 39, 35), (255, 112, 67), (255, 204, 128)]),
    "sintel": ("Sintel", [(13, 27, 62), (79, 195, 247), (255, 138, 101)]),
    "tears": ("Tears of Steel", [(10, 10, 20), (0, 172, 193), (178, 235, 242)]),
    "showcase": ("Bigger Screens", [(49, 27, 146), (236, 64, 122), (255, 202, 40)]),
}

def art(w, h, colors, seed, title=None, subtitle=None):
    rnd = random.Random(seed)
    base = Image.new("RGB", (w, h), colors[0])
    glow = Image.new("RGB", (w, h), colors[0])
    d = ImageDraw.Draw(glow)
    for i in range(7):
        c = colors[1 + i % 2]
        r = rnd.randint(min(w, h) // 4, max(w, h) // 2)
        x, y = rnd.randint(-r // 2, w), rnd.randint(-r // 2, h)
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    glow = glow.filter(ImageFilter.GaussianBlur(max(w, h) / 9))
    img = Image.blend(base, glow, 0.85)
    # diagonal light streaks
    streak = Image.new("L", (w, h), 0)
    sd = ImageDraw.Draw(streak)
    for i in range(3):
        off = rnd.randint(0, w)
        sd.polygon([(off, 0), (off + w // 10, 0), (off - h // 2 + w // 10, h), (off - h // 2, h)], fill=40)
    streak = streak.filter(ImageFilter.GaussianBlur(20))
    img = Image.composite(Image.new("RGB", (w, h), (255, 255, 255)), img, streak)
    # bottom fade
    fade = Image.linear_gradient("L").resize((w, h))
    img = Image.composite(Image.new("RGB", (w, h), (5, 7, 13)), img, fade.point(lambda v: int(v * 0.75)))
    if title:
        dr = ImageDraw.Draw(img)
        size = int(w * 0.11)
        font = ImageFont.truetype(BOLD, size)
        words, lines, cur = title.split(), [], ""
        for wd in words:
            t = (cur + " " + wd).strip()
            if dr.textlength(t, font=font) > w * 0.86 and cur:
                lines.append(cur); cur = wd
            else:
                cur = t
        lines.append(cur)
        y = h - int(h * 0.09) - len(lines) * int(size * 1.1)
        for ln in lines:
            dr.text((int(w * 0.07), y), ln, font=font, fill=(255, 255, 255))
            y += int(size * 1.1)
        if subtitle:
            dr.text((int(w * 0.07), y + 4), subtitle, font=ImageFont.truetype(MED, int(size * 0.38)), fill=(220, 225, 240))
    return img

for key, (title, colors) in TITLES.items():
    seed = sum(map(ord, key))
    art(400, 600, colors, seed, title, "OPEN MOVIE" if key != "showcase" else "DEMO SERIES").save(f"{OUT}/demo_{key}_poster.jpg", quality=86)
    art(1280, 720, colors, seed + 1).save(f"{OUT}/demo_{key}_backdrop.jpg", quality=84)
for i in range(1, 6):
    art(640, 360, TITLES["showcase"][1], 100 + i * 7).save(f"{OUT}/demo_showcase_e{i}.jpg", quality=84)
