#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 path/to/release.apk" >&2
    exit 2
fi

python3 - "$1" <<'PY'
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

apk = Path(sys.argv[1]).resolve()
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")
ndk = Path(os.environ.get("ANDROID_NDK_HOME") or sdk / "ndk/28.2.13676358")
zipalign = Path(os.environ.get("ZIPALIGN") or sdk / "build-tools/36.0.0/zipalign")
readelf_candidates = sorted(ndk.glob("toolchains/llvm/prebuilt/*/bin/llvm-readelf"))
readelf = os.environ.get("LLVM_READELF") or (str(readelf_candidates[0]) if readelf_candidates else None)
if not apk.is_file() or not zipalign.is_file() or not readelf or not Path(readelf).is_file():
    sys.exit("APK, SDK zipalign or NDK llvm-readelf missing; set ANDROID_HOME/ANDROID_NDK_HOME (or ZIPALIGN/LLVM_READELF).")

subprocess.run([str(zipalign), "-c", "-P", "16", "4", str(apk)], check=True)
failures = []
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="triptrack-16kb-") as temporary:
    libraries = [entry for entry in archive.infolist() if entry.filename.endswith(".so")]
    if not libraries:
        sys.exit("No .so files found: expected the fog, MapLibre and Sentry NDK libraries.")
    for index, entry in enumerate(libraries):
        library = Path(temporary) / f"{index}.so"
        library.write_bytes(archive.read(entry))
        output = subprocess.run([readelf, "--file-header", "--program-headers", "--wide", str(library)],
                                check=True, text=True, capture_output=True).stdout
        elf_class = re.search(r"Class:\s*(ELF32|ELF64)\b", output)
        loads = [line.split() for line in output.splitlines() if re.match(r"\s*LOAD\s", line)]
        # Android's 16 KB devices are 64-bit. MapLibre's 32-bit builds use 4 KB; those ABIs cannot
        # run in a 16 KB process. Inspect them too, without dropping support for 32-bit phones.
        page_size = 4096 if elf_class and elf_class.group(1) == "ELF32" else 16384
        aligned = bool(elf_class and loads) and all(int(load[-1], 0) >= page_size for load in loads)
        # A large p_align also requires matching file/virtual-address offsets modulo the page size.
        aligned = aligned and all((int(load[1], 0) - int(load[2], 0)) % page_size == 0 for load in loads)
        print(f"{'PASS' if aligned else 'FAIL'} {entry.filename}: {elf_class.group(1) if elf_class else 'unknown ELF'}, "
              f"required {page_size // 1024} KB, LOAD alignments {', '.join(load[-1] for load in loads) or 'missing'}")
        if not aligned:
            failures.append(entry.filename)
if failures:
    sys.exit(f"16 KB ELF check failed for {len(failures)} libraries.")
print(f"16 KB ZIP and ELF checks passed for all {len(libraries)} libraries.")
PY
