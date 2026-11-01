#!/usr/bin/env python3
"""Export Play artwork from iOS D2, using the Android launcher's source pipeline.

Requires Pillow and Chromium, or --resvg-module with an installed @resvg/resvg-js.
No network, app dependencies or writes to the source checkout.
"""

import argparse
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
OUTPUT = Path(__file__).resolve().parent


def render(svg, width, height, chrome, resvg_module):
    with tempfile.TemporaryDirectory(prefix="triptrack-play-") as temporary:
        work = Path(temporary)
        destination = work / "render.png"
        if resvg_module:
            source = work / "art.svg"
            source.write_text(svg, encoding="utf-8")
            subprocess.run([
                "node", "-e",
                'const fs=require("fs");const {Resvg}=require(process.argv[1]);'
                'fs.writeFileSync(process.argv[3],new Resvg(fs.readFileSync(process.argv[2])).render().asPng());',
                str(resvg_module), str(source), str(destination),
            ], check=True, timeout=60)
        else:
            html = work / "art.html"
            html.write_text('<!doctype html><meta charset="utf-8"><style>'
                            'html,body{margin:0;overflow:hidden}svg{display:block}</style>' + svg,
                            encoding="utf-8")
            subprocess.run([
                str(chrome), "--headless", "--disable-gpu", "--hide-scrollbars",
                "--no-first-run", "--no-default-browser-check", "--disable-background-networking",
                "--force-device-scale-factor=1", f"--window-size={width},{height}",
                f"--user-data-dir={work / 'profile'}", f"--screenshot={destination}", html.as_uri(),
            ], check=True, capture_output=True, timeout=60)
        with Image.open(destination) as image:
            return image.convert("RGB")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ios", type=Path, default=Path.home() / "OneZeeProjects/trip-track-ios-ref")
    parser.add_argument("--chrome", type=Path)
    parser.add_argument("--resvg-module", type=Path)
    args = parser.parse_args()
    source = args.ios / "Tools/build_app_icons.py"
    spec = importlib.util.spec_from_file_location("ios_icons", source)
    ios = importlib.util.module_from_spec(spec)
    sys.dont_write_bytecode = True
    spec.loader.exec_module(ios)
    cached = sorted((Path.home() / "Library/Caches/ms-playwright").glob(
        "chromium_headless_shell-*/chrome-headless-shell-mac-*/chrome-headless-shell"), reverse=True)
    chrome = args.chrome or next(iter(cached), Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
    if not args.resvg_module and not chrome.is_file():
        parser.error("Chromium not found; pass --chrome or --resvg-module")

    # Same artwork() normalization as tools/icons/build_android_icons.py: no board mask/edge or DEV.
    authored = ios.artwork("D2.svg")
    icon = render(authored, 2048, 2048, chrome, args.resvg_module)
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(OUTPUT / "icon-512.png", optimize=True)

    # Continue D2's paper across the wide canvas; the unmasked drawing keeps its aspect ratio.
    # Composite the normalized raster: resvg's clipped non-square SVG/filter path can panic.
    feature = Image.new("RGB", (2048, 1000))
    paper_top, paper_bottom = (241, 230, 207), (230, 214, 182)
    draw = ImageDraw.Draw(feature)
    for y in range(1000):
        fraction = max(0, min(1, (y - 10) / 1044))
        color = tuple(round(a + (b - a) * fraction) for a, b in zip(paper_top, paper_bottom))
        draw.line((0, y, 2047, y), fill=color)
    art = icon.resize((1044, 1044), Image.Resampling.LANCZOS)
    mask = Image.new("L", art.size, 255)
    fade = ImageDraw.Draw(mask)
    for x in range(96):
        fade.line((x, 0, x, 1043), fill=round(x / 95 * 255))
    feature.paste(art, (1030, 10), mask)

    # Inter and the October iPhone screenshot headline keep this in the product's visual language.
    draw = ImageDraw.Draw(feature)
    fonts = ROOT / "core/designsystem/src/main/res/font"
    brand = ImageFont.truetype(str(fonts / "inter_semibold.ttf"), 42)
    headline = ImageFont.truetype(str(fonts / "inter_bold.ttf"), 112)
    ink = "#24262B"
    draw.text((160, 156), "TripTrack", font=brand, fill=ink)
    draw.text((152, 326), "Your roads.", font=headline, fill=ink)
    draw.text((152, 462), "Your atlas.", font=headline, fill=ink)
    feature.resize((1024, 500), Image.Resampling.LANCZOS).save(OUTPUT / "feature-1024x500.png", optimize=True)
    for name, size in (("icon-512.png", (512, 512)), ("feature-1024x500.png", (1024, 500))):
        with Image.open(OUTPUT / name) as image:
            if image.size != size or image.mode != "RGB" or image.format != "PNG":
                raise ValueError(f"Invalid Play asset: {name}")
            print(f"{name}: {image.width}x{image.height}, RGB PNG, { (OUTPUT / name).stat().st_size } bytes")


if __name__ == "__main__":
    main()
