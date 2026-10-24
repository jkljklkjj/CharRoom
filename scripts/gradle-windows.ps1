[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$GradleExecutable,
    [string]$SocketDirectory,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments
)

# Windows JDK selectors use AF_UNIX internally. Some launch environments cannot
# connect sockets bound under AppData/Temp; use a directory in the working tree.
# JAVA_TOOL_OPTIONS reaches both the Gradle client and its forked JVMs.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($env:OS -ne 'Windows_NT') { throw 'This launcher is for Windows; use Gradle directly on other platforms.' }

if (!$GradleExecutable) {
    $wrapperJar = Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar'
    $wrapperProperties = Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.properties'
    if ((Test-Path -LiteralPath $wrapperJar) -and (Test-Path -LiteralPath $wrapperProperties)) {
        $GradleExecutable = Join-Path $projectRoot 'gradlew.bat'
    } else {
        $installed = Get-Command gradle.bat -CommandType Application -ErrorAction SilentlyContinue
        if ($installed) {
            $GradleExecutable = $installed.Source
        } else {
            $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
            $cached = @(Get-ChildItem -Path (Join-Path $cacheRoot 'wrapper/dists/gradle-*/*/gradle-*/bin/gradle.bat') -File -ErrorAction SilentlyContinue)
            if ($cached.Count -ne 1) { throw 'Gradle Wrapper is missing and the cached version is ambiguous or unavailable. Supply -GradleExecutable <installed gradle.bat>; this script does not restore or download Gradle.' }
            $GradleExecutable = $cached[0].FullName
        }
    }
}
$gradleCommand = (Get-Command $GradleExecutable -CommandType Application -ErrorAction Stop).Source
$javaCommand = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    (Get-Command java.exe -CommandType Application -ErrorAction Stop).Source
}
if (!(Test-Path -LiteralPath $javaCommand -PathType Leaf)) { throw 'JAVA_HOME does not provide bin/java.exe.' }

if (!$SocketDirectory) { $SocketDirectory = Join-Path $projectRoot '.gradle/java-sockets' }
$SocketDirectory = [IO.Path]::GetFullPath($SocketDirectory)
New-Item -ItemType Directory -Force -Path $SocketDirectory | Out-Null
if (!$GradleArguments) { $GradleArguments = @('help') }
$previousOptions = $env:JAVA_TOOL_OPTIONS
$buildExitCode = 1
Push-Location -LiteralPath $projectRoot
try {
    $socketOption = '-Djdk.net.unixdomain.tmpdir="' + $SocketDirectory + '"'
    $env:JAVA_TOOL_OPTIONS = (@($previousOptions, $socketOption) | Where-Object { $_ }) -join ' '
    & $javaCommand (Join-Path $PSScriptRoot 'diagnostics/JavaNioProbe.java')
    if ($LASTEXITCODE -ne 0) { throw 'Java local-socket initialization failed. Choose -SocketDirectory outside AppData, using a short local path.' }
    & $gradleCommand @GradleArguments
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    Pop-Location
}
exit $buildExitCode
