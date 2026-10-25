#!/usr/bin/env python3
"""Read-only CharRoom prerequisites. Does not build, install, or contact services."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def version(executable, args):
    try:
        result = subprocess.run([executable, *args], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=12)
        return result.returncode, (result.stdout + result.stderr).strip()
    except (OSError, subprocess.TimeoutExpired) as exc:
        return -1, type(exc).__name__


def inspect(client, target):
    root = Path(client).resolve()
    if not (root / "build.gradle.kts").is_file() or not (root / "web/package.json").is_file():
        raise ValueError("client must be the CharRoom root with build.gradle.kts and web/package.json")
    checks = []

    def add(name, status, detail):
        checks.append({"check": name, "status": status, "detail": detail})

    if target in ("web", "all"):
        node = shutil.which("node")
        code, output = version(node, ["--version"]) if node else (-1, "not found")
        major = re.search(r"v(\d+)", output)
        ok = code == 0 and major is not None and int(major[1]) >= 20
        add("node", "PASS" if ok else "BLOCK", output.splitlines()[0] if output else "unknown")
        add("npm", "PASS" if shutil.which("npm") else "BLOCK", "executable lookup only")
        add("lockfile", "PASS" if (root / "web/package-lock.json").is_file() else "BLOCK", "web/package-lock.json")
        add("web dependencies", "PASS" if (root / "web/node_modules").is_dir() else "WARN", "directory presence only; use npm ci when needed")
        package = json.loads((root / "web/package.json").read_text(encoding="utf-8-sig"))
        scripts = package.get("scripts", {})
        add("web build script", "PASS" if "build" in scripts else "BLOCK", "declared; not executed")
        absent = [s for s in ("test", "lint", "typecheck") if s not in scripts]
        add("web test tooling", "WARN" if absent else "PASS", "missing scripts: " + ", ".join(absent) if absent else "scripts declared; not executed")

    if target in ("desktop", "android", "all"):
        build = (root / "build.gradle.kts").read_text(encoding="utf-8-sig")
        required = re.search(r"jvmToolchain\s*\(\s*(\d+)\s*\)", build)
        java_home = os.environ.get("JAVA_HOME")
        # gradlew prefers JAVA_HOME when set; do not silently check a different PATH Java.
        java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
        code, output = version(java, ["-version"]) if java else (-1, "not found")
        found = re.search(r'version\s+"(\d+)', output)
        if code != 0 or not found:
            add("gradle launcher Java", "BLOCK", "Java version unavailable; check JAVA_HOME/PATH")
        else:
            matches = required is not None and found[1] == required[1]
            add("gradle launcher Java", "PASS" if matches else "WARN",
                f"launcher={found[1]}, configured toolchain={required[1] if required else 'unknown'}; toolchain availability/build not verified")
        for name in ("gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                     "gradlew.bat" if os.name == "nt" else "gradlew"):
            add(name, "PASS" if (root / name).is_file() else "BLOCK", "file presence only")
        test_dirs = ("src/commonTest/kotlin", "src/desktopTest/kotlin", "src/androidUnitTest/kotlin")
        has_tests = any(any((root / folder).rglob("*.kt")) for folder in test_dirs)
        add("JVM tests", "PASS" if has_tests else "WARN", "Kotlin test files found" if has_tests else "no Kotlin tests in configured directories; NO-SOURCE is not coverage")

    if target in ("android", "all"):
        # Do not dump local.properties or arbitrary environment values.
        configured_env = any(Path(os.environ[key]).is_dir() for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT") if os.environ.get(key))
        local_file = root / "local.properties"
        local_hint = local_file.is_file() and bool(re.search(r"(?m)^sdk\.dir\s*=\s*\S", local_file.read_text(encoding="utf-8-sig")))
        add("Android SDK", "PASS" if configured_env else "WARN" if local_hint else "BLOCK",
            "SDK directory exists via environment; packages not checked" if configured_env else
            "local.properties has sdk.dir; directory/packages not validated" if local_hint else "no SDK environment directory or local sdk.dir hint")
        add("Android module task", "WARN", "use :androidApp:assembleDebug; conditional settings include requires an android task name")

    return {"target": target, "scope": "prerequisites only; no build or service verification",
            "blocked": sum(c["status"] == "BLOCK" for c in checks), "checks": checks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True, type=Path)
    parser.add_argument("--target", choices=("web", "desktop", "android", "all"), default="all")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    try:
        report = inspect(args.client, args.target)
    except (OSError, ValueError) as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=True))
        return 2
    if args.json:
        print(json.dumps(report, ensure_ascii=True, indent=2))
    else:
        for check in report["checks"]:
            print(f"{check['status']:5} {check['check']}: {check['detail']}")
        print(report["scope"])
    return 1 if report["blocked"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
