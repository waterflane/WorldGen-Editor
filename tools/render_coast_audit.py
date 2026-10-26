"""Render matched offline terrain audits. Requires Pillow; never reads game saves."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

parser = argparse.ArgumentParser()
parser.add_argument("--before", default="build/preview-tests/coast-audit-before.json")
parser.add_argument("--after", default="build/preview-tests/coast-audit-after.json")
parser.add_argument("--output", default="build/natural-coast-comparison.png")
args = parser.parse_args()
before, after = [json.loads(Path(p).read_text()) for p in (args.before, args.after)]
assert before["seed"] == after["seed"]
rows = [before["custom"], after["custom"]]
assert [(r[0], r[1]) for r in rows[0]] == [(r[0], r[1]) for r in rows[1]]
image = Image.new("RGB", (1120, 760), "#121c24")
draw = ImageDraw.Draw(image)
font_path = Path("C:/Windows/Fonts/segoeui.ttf")
def font(size):
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default()
def color(h):
    if h < 63:
        t = max(0, min(1, (h + 32) / 95))
        return (int(15 + 25*t), int(40 + 110*t), int(80 + 85*t))
    t = max(0, min(1, (h - 63) / 90))
    return (int(60 + 170*t), int(140 + 50*t), int(65 + 130*t))
draw.text((28, 14), "Natural coast regression | seed " + before["seed"], fill="white", font=font(25))
draw.text((28, 48), "Solid base heights, excluding water and vegetation. Identical mask and seed; 8-block sample grid.", fill="#b3c6d3", font=font(15))
for i, (title, samples) in enumerate(zip(("Before: final-density height envelope", "After: pre-cave natural depth"), rows)):
    ox, oy = 28 + 548*i, 116
    draw.text((ox, 84), title, fill="white", font=font(19))
    for x, z, h, *_ in samples:
        px, py = ox + (x+128)//8*5, oy + (z+384)//8*5
        draw.rectangle((px, py, px+4, py+4), fill=color(h))
    for x, z, label in ((96, -24, "River"), (328, -168, "Jungle")):
        px, py = ox+(x+128)//8*5+2, oy+(z+384)//8*5+2
        draw.line((px-6, py, px+6, py), fill="white", width=2)
        draw.line((px, py-6, px, py+6), fill="white", width=2)
        draw.text((px+8, py-12), label, fill="white", font=font(13))
    draw.text((ox, 526), "x -128..704 | z -384..256 | darker blue = deeper seabed", fill="#b3c6d3", font=font(13))
x0, x1, y0, y1 = 54, 1070, 590, 708
profiles = [[r for r in samples if r[1] == -168] for samples in rows]
low, high = -32, 128
def point(row):
    return (x0+(row[0]+128)/832*(x1-x0), y1-(row[2]-low)/(high-low)*(y1-y0))
for h in (0, 32, 63, 96, 128):
    y = y1-(h-low)/(high-low)*(y1-y0)
    draw.line((x0, y, x1, y), fill="#3a4a55")
    draw.text((15, y-9), str(h), fill="#a9becb", font=font(12))
for samples, c in zip(profiles, ("#ef8989", "#7de1b6")):
    draw.line([point(r) for r in samples], fill=c, width=2)
draw.text((54, 559), "Solid-ground transect z=-168 | red: previous rules | green: natural coast | horizontal line Y=63: sea level", fill="white", font=font(14))
draw.text((54, 724), "Base terrain audit is separate from finished-chunk material checks and mod/preview parity.", fill="#b3c6d3", font=font(14))
Path(args.output).parent.mkdir(parents=True, exist_ok=True)
image.save(args.output)
print(args.output)
