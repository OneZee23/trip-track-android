"""Prints the bench results of scripts/measure.sh as Markdown tables.

    python3 -I scripts/summarise.py out/measure/bench
    python3 -I scripts/summarise.py out/rounds/bench --rounds
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
        # Repeated rounds are told apart by the file name: bench-a-stress-fog-r2.json.
        data["style"] = path.stem.split("-", 3)[3]
        rows.append(data)

    print("| Candidate | Dataset | Style | Phase | Frames | fps | Frame p50 | p95 | p99 | max | >25 ms | Fog CPU p50 | p95 | p99 | Fog encode p50 | p95 | Map frame CPU p50 | p95 | Segments/frame p95 |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for data in rows:
        for phase, p in data["phases"].items():
            interval = p["intervalMs"]
            cpu = p["fogCpuMs"]
            seg = p["segmentsPerFrame"]
            frame = p.get("mapFrameCpuMs", {})
            encode = p.get("fogEncodeMs", {})
            print(
                f"| {data['candidate']} | {data['dataset']} | {data['style']} | {phase} | {p['frames']} | {fmt(p['fps'], 0)} "
                f"| {fmt(interval.get('p50'))} | {fmt(interval.get('p95'))} | {fmt(interval.get('p99'))} | {fmt(interval.get('max'))} "
                f"| {p['over25ms']} | {fmt(cpu.get('p50'), 2)} | {fmt(cpu.get('p95'), 2)} | {fmt(cpu.get('p99'), 2)} "
                f"| {fmt(encode.get('p50'), 2)} | {fmt(encode.get('p95'), 2)} "
                f"| {fmt(frame.get('p50'))} | {fmt(frame.get('p95'))} | {fmt(seg.get('p95'), 0)} |"
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




def rounds(folder):
    """Median over the repeated rounds (bench-*-rN.json) of each candidate, dataset and phase."""
    import statistics

    groups = {}
    for path in sorted(pathlib.Path(folder).glob("bench-*-r*.json")):
        _, cand, data, rest = path.stem.split("-", 3)
        for phase, p in json.loads(path.read_text())["phases"].items():
            groups.setdefault((cand, data, phase), []).append(p)

    def med(items, *keys):
        values = []
        for item in items:
            value = item
            for key in keys:
                value = value.get(key) if isinstance(value, dict) else None
            if value is not None:
                values.append(value)
        return statistics.median(values) if values else None

    print("| Candidate | Dataset | Phase | Runs | fps | Frame p50 ms | p95 | p99 | Frames > 25 ms | Fog encode p50 ms | p95 | Map frame CPU p50 ms |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    order = {"none": 0, "a": 1, "b": 2, "c": 3}
    for (cand, data, phase), items in sorted(groups.items(), key=lambda kv: (kv[0][1], kv[0][2], order.get(kv[0][0], 9))):
        encode = med(items, "fogEncodeMs", "p50")
        print(
            f"| {cand} | {data} | {phase} | {len(items)} | {fmt(med(items, 'fps'), 0)} "
            f"| {fmt(med(items, 'intervalMs', 'p50'))} | {fmt(med(items, 'intervalMs', 'p95'))} | {fmt(med(items, 'intervalMs', 'p99'))} "
            f"| {fmt(med(items, 'over25ms'), 0)} | {fmt(encode, 2) if cand == 'a' else '–'} "
            f"| {fmt(med(items, 'fogEncodeMs', 'p95'), 2) if cand == 'a' else '–'} | {fmt(med(items, 'mapFrameCpuMs', 'p50'))} |"
        )


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[2] == "--rounds":
        rounds(sys.argv[1])
    else:
        main(sys.argv[1] if len(sys.argv) > 1 else "out/measure/bench")
