"""Builds the comparison images for docs/fog-spike.md from the screenshots of measure.sh.

    python3 -I scripts/composite.py out/measure/shots ../../docs/assets/fog-spike out/look

Full screenshots are 1080 × 2400; the doc gets them side by side at a third of the width, and
the mid-pinch frames as 1:1 crops, where blur and edges are visible.
"""
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

LABEL_HEIGHT = 44


def font(size):
    for name in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def labelled(image, text):
    out = Image.new("RGB", (image.width, image.height + LABEL_HEIGHT), "white")
    out.paste(image, (0, LABEL_HEIGHT))
    ImageDraw.Draw(out).text((10, 8), text, fill="black", font=font(26))
    return out


def row(images, gap=8):
    width = sum(i.width for i in images) + gap * (len(images) - 1)
    height = max(i.height for i in images)
    out = Image.new("RGB", (width, height), "white")
    x = 0
    for image in images:
        out.paste(image, (x, 0))
        x += image.width + gap
    return out


def scaled(path, width=360):
    image = Image.open(path).convert("RGB")
    return image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)


def crop(path, size=540):
    image = Image.open(path).convert("RGB")
    left = (image.width - size) // 2
    top = (image.height - size) // 2
    return image.crop((left, top, left + size, top + size))


def save(image, path):
    image.save(path, "JPEG", quality=82, optimize=True)
    print(path, f"{path.stat().st_size // 1024} KB")


def main(shots, out, extra):
    """`extra` holds the frames taken by hand: the small-set city views of A, B and C, and C's
    stress-set frame in the middle of a zoom."""
    shots = pathlib.Path(shots)
    extra = pathlib.Path(extra)
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    names = {"a": "A custom layer", "b": "B polygon fill", "c": "C raster tiles", "none": "Map alone"}

    for cand, title in names.items():
        frames = []
        for preset in ("street", "city", "region", "country"):
            path = shots / f"{cand}-stress-{preset}.png"
            if path.exists():
                frames.append(labelled(scaled(path, 300), f"{title}, {preset}"))
        if frames:
            save(row(frames), out / f"zooms-{cand}.jpg")

    # The same corridors at rest, 1:1: A's feather, B's hard edge, C like A.
    box = (560, 950, 1040, 1430)
    edges = []
    for cand, name in (("a", "first-a-small-city.png"), ("b", "check-b-small-city.png"), ("c", "check-c-small-city.png")):
        if (extra / name).exists():
            edges.append(labelled(Image.open(extra / name).convert("RGB").crop(box), f"{names[cand]}, 1:1"))
    if edges:
        save(row(edges), out / "edges-small-city.jpg")

    # C in the middle of a zoom on the stress set: fogged tiles next to tiles not drawn yet.
    if (shots / "c-stress-midpinch.png").exists():
        frame = Image.open(shots / "c-stress-midpinch.png").convert("RGB").crop((0, 200, 1080, 1100))
        save(labelled(frame, "C raster tiles, stress set, mid-zoom, 1:1"), out / "c-tiles-midzoom.jpg")

    styles = []
    for style, path in (("fog", "a-stress-city.png"), ("night", "a-stress-city-night.png"), ("cells", "a-stress-city-cells.png")):
        if (shots / path).exists():
            styles.append(labelled(scaled(shots / path, 300), f"A, {style}"))
    if styles:
        save(row(styles), out / "styles-a.jpg")

    if (shots / "a-small-street.png").exists():
        save(labelled(crop(shots / "a-small-street.png", 720), "A, small set, street zoom, 1:1"), out / "a-small-street.jpg")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
