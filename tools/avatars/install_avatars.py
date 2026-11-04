#!/usr/bin/env python3
"""Installs TripTrack's avatar artwork as Android drawables (the iPhone app ships the same images).

Usage: python3 tools/avatars/install_avatars.py <dir with the 512 px masters named by code point, e.g. 1f60e.png>
Writes app/src/main/res/drawable-nodpi/avatar_<code>.webp at 256 px: the largest avatar (the profile's 52 sp glyph) is
about 70 dp, 210 px on a 3x screen.
"""
import pathlib
import sys
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/src/main/res/drawable-nodpi'
CODES = ['1f60e', '1f642', '1f604', '1f920', '1f978', '1f9d4', '1f468_200d_1f9b3', '1f469', '1f469_200d_1f9b0',
         '1f471_200d_2640', '1f475', '1f436', '1f305', '1f3b8', '1f4f7', '1f3ae', '1f680', '1f699']

src = pathlib.Path(sys.argv[1])
for code in CODES:
    image = Image.open(src / f'{code}.png').convert('RGBA').resize((256, 256), Image.LANCZOS)
    path = OUT / f'avatar_{code}.webp'
    image.save(path, 'WEBP', quality=92, method=6)
    print(path.relative_to(ROOT), path.stat().st_size)
