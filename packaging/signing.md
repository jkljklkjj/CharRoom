# Windows 签名与 macOS 公证

桌面构建与产物收集见 [打包说明](README.md)。签名使用 CI secrets，不将证书、密码或 API 私钥纳入 Git。

## Windows

- `WINDOWS_PFX`：代码签名 PFX 的 base64 内容。
- `WINDOWS_PFX_PASSWORD`：PFX 密码。两项均缺失时 CI 跳过签名；仅提供其中一项时报错。
- `scripts/sign-windows.ps1` 优先寻找 PATH 上的 `signtool.exe`，再查找 Windows SDK 版本目录下的 x64 工具；也可用 `-SignToolPath` 指定工具。

```powershell
# 环境变量已通过安全方式注入；对最终上传副本执行
./scripts/sign-windows.ps1 -Files 'build/release-artifacts/desktop/*.msi'
```

脚本先检查全部输入，缺失的通配符或非 MSI/EXE 文件使任务失败；重复匹配的文件只签一次。每个文件执行 SHA-256 签名和时间戳，再执行 `signtool verify /pa /all`，任何非零退出码使 CI 失败。PFX 写入唯一临时文件，直接传给工具，完成或失败均清理，不导入 Windows 证书库。签名后不要再次运行依赖安装器构建的收集任务，以免重建覆盖签名。

离线回归使用假 signtool 和无效的测试 PFX，不访问真实证书库、时间戳服务或生产安装器：

```powershell
python scripts/tests/test_windows_signing.py
```

覆盖正常签名验证、签名失败、验证失败、文件缺失、凭据缺失、重叠通配符去重及临时证书清理。Windows 上需要 `pwsh`；其他系统跳过，不能当作签名测试通过。

## macOS

- `MACOS_IDENTITY`：Developer ID Application 签名身份。
- `APPLE_API_KEY_BASE64`：App Store Connect API 私钥 base64。
- `APPLE_KEY_ID`、`APPLE_ISSUER_ID`：对应 API key 与 issuer 标识。

需要 macOS、Xcode 命令行工具及 Apple Developer 凭据。应用签名可用 `scripts/sign-macos.sh /path/to/App.app`；DMG 公证可用 `scripts/notarize-macos.sh /path/to/App.dmg`。现有 macOS 工作流需要在 macOS 上单独验证，Windows 的假签名测试不覆盖它。

免安装 ZIP 是独立分发产物，不能仅凭 DMG 的公证推定 ZIP 已公证。工作流在上述凭据与 `MACOS_IDENTITY` 均提供时，签名发布镜像的 `.app` 并校验，使用 `ditto` 创建 ZIP 后提交公证；成功后将票据 staple 到 `.app`，再重建最终 ZIP。ZIP 本身不能 staple。缺少凭据时保留未签名/未公证 ZIP；Windows ZIP 内的启动器也不会被现有 MSI 签名步骤签名。详见 [Apple 官方流程](https://developer.apple.com/documentation/security/customizing-the-notarization-workflow)。

## 本轮验证

2026-10-28：六项 Windows 离线签名回归通过，包含带空格的工具/安装器路径。未使用真实代码签名证书，也未执行真实签名、公证或联网时间戳验证。
