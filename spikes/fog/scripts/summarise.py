"""Prints the bench results of scripts/measure.sh as Markdown tables.

    python3 -I scripts/summarise.py out/measure/bench
"""
import json
import pathlib
import sys


def fmt(value, digits=1):
    if value is None:
        return "–"
    return f"{value:.{digits}f}"


def main(folder):
    rows = []
    for path in sorted(pathlib.Path(folder).glob("bench-*.json")):
        data = json.loads(path.read_text())
        rows.append(data)

    print("| Candidate | Dataset | Style | Phase | Frames | fps | Frame p50 | p95 | p99 | max | >25 ms | Fog CPU p50 | p95 | p99 | Segments/frame p95 |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for data in rows:
        for phase, p in data["phases"].items():
            interval = p["intervalMs"]
            cpu = p["fogCpuMs"]
            seg = p["segmentsPerFrame"]
            print(
                f"| {data['candidate']} | {data['dataset']} | {data['style']} | {phase} | {p['frames']} | {fmt(p['fps'], 0)} "
                f"| {fmt(interval.get('p50'))} | {fmt(interval.get('p95'))} | {fmt(interval.get('p99'))} | {fmt(interval.get('max'))} "
                f"| {p['over25ms']} | {fmt(cpu.get('p50'), 2)} | {fmt(cpu.get('p95'), 2)} | {fmt(cpu.get('p99'), 2)} | {fmt(seg.get('p95'), 0)} |"
            )

    print()
    print("| Candidate | Dataset | Style | Segments fine/mid/far | Reveal ms | Mesh ms | Mesh KB | PSS MB | Graphics MB | Native heap MB |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for data in rows:
        prep = data.get("prepare") or {}
        mem = data.get("memory") or {}

        def mb(key):
            raw = mem.get(key)
            return fmt(int(raw) / 1024, 0) if raw not in (None, "") else "–"

        print(
            f"| {data['candidate']} | {data['dataset']} | {data['style']} "
            f"| {prep.get('segmentsFine')}/{prep.get('segmentsMid')}/{prep.get('segmentsFar')} "
            f"| {fmt(prep.get('revealMs'), 0)} | {fmt(prep.get('meshMs'), 0)} | {fmt(prep.get('meshBytes', 0) / 1024, 0)} "
            f"| {mb('totalPssKb')} | {mb('graphicsKb')} | {mb('nativeHeapKb')} |"
        )


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "out/measure/bench")
