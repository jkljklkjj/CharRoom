#!/usr/bin/env python3
"""Build the pinned JNI crate with an installed Android NDK; no downloads or SDK mutations."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess

ABIS = {"arm64-v8a": ("aarch64-linux-android", "aarch64-linux-android"),
        "x86_64": ("x86_64-linux-android", "x86_64-linux-android")}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--abis", default="arm64-v8a,x86_64")
    args = parser.parse_args()
    crate = Path(__file__).resolve().parents[1] / "native" / "quiche-jni"
    host = {"Windows": "windows-x86_64", "Linux": "linux-x86_64", "Darwin": "darwin-x86_64"}[platform.system()]
    toolchain = args.ndk.resolve() / "toolchains" / "llvm" / "prebuilt" / host
    if not toolchain.is_dir():
        parser.error("The selected Android NDK has no toolchain for this host")
    cargo = shutil.which("cargo")
    if not cargo:
        parser.error("Rust/cargo is required; install rustup and the Android target(s) first")
    if not shutil.which("cmake"):
        parser.error("CMake is required by quiche/BoringSSL; add it to PATH first")
    env = os.environ.copy()
    env["ANDROID_NDK_HOME"] = str(args.ndk.resolve())
    env["ANDROID_NDK_ROOT"] = str(args.ndk.resolve())
    env["PATH"] = str(toolchain / "bin") + os.pathsep + env["PATH"]
    env["CARGO_BUILD_JOBS"] = env.get("CARGO_BUILD_JOBS", "2")
    cargo_target = Path(env.get("CARGO_TARGET_DIR", crate / "target"))
    if not cargo_target.is_absolute():
        cargo_target = crate / cargo_target
    cargo_target = cargo_target.resolve()
    env["CARGO_TARGET_DIR"] = str(cargo_target)
    # NDK r27 and newer support Android's 16 KiB page-size devices. Apply the
    # same alignment to Rust's final shared-library link as to BoringSSL.
    for abi in args.abis.split(","):
        if abi not in ABIS:
            parser.error(f"Unsupported ABI: {abi}")
        target, clang_prefix = ABIS[abi]
        compiler = toolchain / "bin" / (clang_prefix + "26-clang" + (".cmd" if platform.system() == "Windows" else ""))
        env["CARGO_TARGET_" + target.upper().replace("-", "_") + "_LINKER"] = str(compiler)
        env["CARGO_TARGET_" + target.upper().replace("-", "_") + "_RUSTFLAGS"] = "-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
        # Let the NDK choose C/C++ compilers consistently on both CMake invocations
        # made by boring-sys. Supplying CC wrappers makes CMake discard its Android
        # cache when it switches from ssl to crypto, producing host-architecture objects.
        toolchain_file = cargo_target / "android-toolchains" / (abi + ".cmake")
        toolchain_file.parent.mkdir(parents=True, exist_ok=True)
        toolchain_file.write_text(
            f'set(ANDROID_ABI "{abi}")\n'
            'set(ANDROID_PLATFORM android-26)\n'
            'set(ANDROID_STL c++_shared)\n'
            'set(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES ON)\n'
            f'include([=[{args.ndk.resolve().as_posix()}/build/cmake/android.toolchain.cmake]=])\n', encoding="utf-8")
        env["CMAKE_TOOLCHAIN_FILE_" + target.replace("-", "_")] = str(toolchain_file)
        subprocess.run([cargo, "build", "--locked", "--release", "--lib", "--target", target], cwd=crate, env=env, check=True)
        output = args.output.resolve() / abi
        output.mkdir(parents=True, exist_ok=True)
        shutil.copy2(cargo_target / target / "release" / "libchatlite_quic.so", output / "libchatlite_quic.so")
        # boring-sys links the NDK C++ runtime. It is not a public Android system library.
        shutil.copy2(toolchain / "sysroot" / "usr" / "lib" / clang_prefix / "libc++_shared.so", output / "libc++_shared.so")
        print(f"Built {abi}: {output / 'libchatlite_quic.so'}")

if __name__ == "__main__":
    main()
