param(
    [Parameter(Mandatory = $true)][string]$ProtoJavaDirectory,
    [Parameter(Mandatory = $true)][string]$ProtobufJar,
    [Parameter(Mandatory = $true)][string]$JUnitJar,
    [Parameter(Mandatory = $true)][string]$HamcrestJar,
    [string]$KotlinHome,
    [string]$JniProbeOutputDirectory
)

# Isolated shared-protocol tests. This is not a desktop/Android application build.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$KotlinHome) {
    $compiler = Get-Command kotlinc.bat -ErrorAction Stop
    $KotlinHome = Split-Path -Parent (Split-Path -Parent $compiler.Source)
}
foreach ($file in @($ProtobufJar, $JUnitJar, $HamcrestJar,
    (Join-Path $ProtoJavaDirectory 'MessageProtos.java'),
    (Join-Path $ProtoJavaDirectory 'AgentStreamProtos.java'),
    (Join-Path $KotlinHome 'lib/kotlin-test-junit.jar'))) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Required test dependency is missing: $file" }
}

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('charroom-protocol-' + [guid]::NewGuid().ToString('N'))
$javaOutput = Join-Path $testRoot 'java'
$kotlinOutput = Join-Path $testRoot 'kotlin'
New-Item -ItemType Directory -Force -Path $javaOutput | Out-Null
$testSupport = Join-Path $testRoot 'ActionLoggerTestSupport.kt'
@'
package core
// Action logging/serialization is outside this isolated suite.
enum class ActionType { OTHER }
data class Action(val id: String, val timestamp: Long, val type: ActionType, val targetId: String?, val metadata: Map<String, String>)
object ActionLogger { fun getSnapshot(): List<Action> = emptyList() }
'@ | Set-Content -LiteralPath $testSupport -Encoding utf8

& javac -encoding UTF-8 --release 21 -cp $ProtobufJar -d $javaOutput `
    (Join-Path $ProtoJavaDirectory 'MessageProtos.java') (Join-Path $ProtoJavaDirectory 'AgentStreamProtos.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$classpath = @($javaOutput, $ProtobufJar, $JUnitJar, $HamcrestJar,
    (Join-Path $KotlinHome 'lib/kotlin-test.jar'), (Join-Path $KotlinHome 'lib/kotlin-test-junit.jar')) -join ';'
$sources = @($testSupport)
foreach ($name in @('ProtobufBuilders', 'ServerConfig', 'MessageProtocol', 'ChatTransport',
    'ProtobufMessageDispatcher', 'PendingMessageAcks', 'QuicStreamProtocol', 'QuicDriver', 'NativeQuicChatTransport')) {
    $sources += Join-Path $projectRoot "src/commonMain/kotlin/core/$name.kt"
}
$testClasses = @('MessageProtocolTest', 'ProtobufMessageDispatcherTest', 'PendingMessageAcksTest', 'QuicStreamProtocolTest', 'NativeQuicChatTransportTest')
foreach ($name in $testClasses) { $sources += Join-Path $projectRoot "src/commonTest/kotlin/core/$name.kt" }
& (Join-Path $KotlinHome 'bin/kotlinc.bat') @sources -classpath $classpath -jvm-target 21 -d $kotlinOutput
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$runtimeClasspath = @($kotlinOutput, $classpath, (Join-Path $KotlinHome 'lib/kotlin-stdlib.jar')) -join ';'
Write-Output 'Running shared protocol logic only; ActionLogger is a test double; Netty/Cronet/UI are not loaded.'
& java -cp $runtimeClasspath org.junit.runner.JUnitCore @($testClasses | ForEach-Object { 'core.' + $_ })
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($JniProbeOutputDirectory) {
    $probeSources = @(
        (Join-Path $projectRoot 'native/tests/QuicJniProbe.kt'),
        (Join-Path $projectRoot 'src/androidMain/kotlin/com/chatlite/charroom/core/quic/QuicheNative.kt'),
        (Join-Path $projectRoot 'src/androidMain/kotlin/com/chatlite/charroom/core/quic/QuicheDriver.kt')
    )
    $probeOutput = Join-Path $JniProbeOutputDirectory 'classes'
    New-Item -ItemType Directory -Force -Path $probeOutput | Out-Null
    & (Join-Path $KotlinHome 'bin/kotlinc.bat') @probeSources -classpath $runtimeClasspath -jvm-target 21 -d $probeOutput
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Copy-Item -Path "$javaOutput/*", "$kotlinOutput/*" -Destination $probeOutput -Recurse -Force
    Copy-Item -LiteralPath $ProtobufJar, (Join-Path $KotlinHome 'lib/kotlin-stdlib.jar') -Destination $JniProbeOutputDirectory -Force
    Write-Output "JNI probe classes built: $JniProbeOutputDirectory"
}
exit 0
