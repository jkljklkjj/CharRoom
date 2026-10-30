"""Read-only drift check against the adjacent backend's two protocol definitions."""
import argparse
from pathlib import Path


def check(client: Path, backend: Path) -> int:
    failed = False
    for name in ("message.proto", "agent_stream.proto"):
        source = backend / "src/main/proto" / name
        if not source.is_file():
            print(f"MISSING source: {source}")
            failed = True
            continue
        expected = source.read_text(encoding="utf-8-sig")
        for directory in ("src/main/proto", "proto", "web/src/proto", "web/public/proto"):
            copy = client / directory / name
            if not copy.is_file() or copy.read_text(encoding="utf-8-sig") != expected:
                print(f"DRIFT: {copy.relative_to(client)} differs from backend {name}")
                failed = True
    if not failed:
        print("PASS: both protocols match the backend in all four client locations")
    return int(failed)


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", type=Path, default=root.parent / "chat-room")
    args = parser.parse_args()
    raise SystemExit(check(root, args.backend))
