param(
    [string] $KeyStore,
    [string] $KeyAlias,
    [string] $ExpectedCertificateSha256
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-BuildTool {
    param([string] $ToolPath, [string[]] $Arguments)
    & $ToolPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$(Split-Path -Leaf $ToolPath) exited with code $LASTEXITCODE"
    }
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -or -not $env:JAVA_HOME) {
    throw 'Set ANDROID_HOME to the Android SDK and JAVA_HOME to JDK 17.'
}
$tools = Join-Path $sdk 'build-tools/36.0.0'
$androidJar = Join-Path $sdk 'platforms/android-36/android.jar'
$java = Join-Path $env:JAVA_HOME 'bin'
$source = Join-Path $PSScriptRoot 'app/src/main'
$build = Join-Path $PSScriptRoot 'build'
$apiArchive = Join-Path $build 'deps/api-102.0.0.aar'
$apiJar = Join-Path $build 'deps/libxposed-api.jar'
$apiHash = '423484A6E1807E7A423C4B88FCD8176D104318259D91791877FED88FE91479D0'
if ($KeyStore) {
    $keystore = (Resolve-Path -LiteralPath $KeyStore).Path
    $signingOptions = @('--ks', $keystore)
    if ($KeyAlias) { $signingOptions += @('--ks-key-alias', $KeyAlias) }
    if ($env:HYPEROS_PICKER_STORE_PASSWORD) {
        $signingOptions += @('--ks-pass', 'env:HYPEROS_PICKER_STORE_PASSWORD')
    }
    if ($env:HYPEROS_PICKER_KEY_PASSWORD) {
        $signingOptions += @('--key-pass', 'env:HYPEROS_PICKER_KEY_PASSWORD')
    }
} elseif ($KeyAlias -or $ExpectedCertificateSha256) {
    throw 'Specify -KeyStore when selecting a signing alias or expected certificate.'
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force (Split-Path -Parent $apiArchive) | Out-Null
if (-not (Test-Path -LiteralPath $apiArchive)) {
    Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar' -OutFile $apiArchive
}
if ((Get-FileHash -LiteralPath $apiArchive -Algorithm SHA256).Hash -ne $apiHash) {
    throw "Unexpected checksum for $apiArchive"
}
$archive = [IO.Compression.ZipFile]::OpenRead($apiArchive)
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('classes.jar'), $apiJar, $true)
} finally {
    $archive.Dispose()
}

$scratch = Join-Path $build ([Guid]::NewGuid().ToString())
$generated = Join-Path $scratch 'generated'
$classes = Join-Path $scratch 'classes'
New-Item -ItemType Directory -Force $generated,$classes | Out-Null

try {
    $resources = Join-Path $scratch 'resources.zip'
    $unsigned = Join-Path $scratch 'unsigned.apk'
    $aligned = Join-Path $scratch 'aligned.apk'
    $classesJar = Join-Path $scratch 'classes.jar'
    Invoke-BuildTool (Join-Path $tools 'aapt2.exe') @('compile','--dir',(Join-Path $source 'res'),'-o',$resources)
    Invoke-BuildTool (Join-Path $tools 'aapt2.exe') @('link','--manifest',(Join-Path $source 'AndroidManifest.xml'),
        '-I',$androidJar,'--java',$generated,'--min-sdk-version','33','--target-sdk-version','36',
        '--version-code','10','--version-name','1.6.1','-o',$unsigned,$resources)

    $sources = @(Get-ChildItem -Recurse -File -Filter '*.java' -Path (Join-Path $source 'java'),$generated |
        ForEach-Object { $_.FullName })
    Invoke-BuildTool (Join-Path $java 'javac.exe') (@('--release','17','-encoding','UTF-8',
        '-classpath',"$androidJar;$apiJar",'-d',$classes) + $sources)
    Invoke-BuildTool (Join-Path $java 'jar.exe') @('--create','--file',$classesJar,'-C',$classes,'.')
    Invoke-BuildTool (Join-Path $tools 'd8.bat') @('--release','--min-api','33','--lib',$androidJar,
        '--classpath',$apiJar,'--output',$scratch,$classesJar)

    $archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
    try {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, (Join-Path $scratch 'classes.dex'), 'classes.dex') | Out-Null
        foreach ($file in @('java_init.list','scope.list','module.prop')) {
            $entry = "META-INF/xposed/$file"
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive, (Join-Path $source "resources/$entry"), $entry) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }

    if (-not $KeyStore) {
        $keystore = Join-Path $build 'local-debug.jks'
        if (-not (Test-Path -LiteralPath $keystore)) {
            Invoke-BuildTool (Join-Path $java 'keytool.exe') @('-genkeypair','-keystore',$keystore,
                '-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA',
                '-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
        }
        $signingOptions = @('--ks', $keystore, '--ks-pass', 'pass:android', '--ks-key-alias', 'androiddebugkey')
    }
    $apk = Join-Path $scratch 'HyperOSPicker.apk'
    Invoke-BuildTool (Join-Path $tools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
    $apkSigner = Join-Path $tools 'apksigner.bat'
    Invoke-BuildTool $apkSigner (@('sign') + $signingOptions + @(
        '--v1-signing-enabled','false',
        '--v2-signing-enabled','false',
        '--v3-signing-enabled','true',
        '--v4-signing-enabled','false',
        '--out',$apk,$aligned))
    $verification = @(Invoke-BuildTool $apkSigner @('verify','--verbose','--print-certs',$apk))
    $verification | Write-Output
    if (-not ($verification -match '^Verified using v3 scheme \(APK Signature Scheme v3\): true$') -or
        ($verification -match '^Verified using v(?:1|2|4) scheme .*: true$')) {
        throw 'The APK was not verified as APK Signature Scheme v3 only.'
    }
    if ($ExpectedCertificateSha256) {
        $expected = $ExpectedCertificateSha256.Replace(':', '').ToLowerInvariant()
        $certificates = @($verification | Select-String '^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$')
        if ($certificates.Count -ne 1 -or $certificates[0].Matches[0].Groups[1].Value -ne $expected) {
            throw 'The APK signing certificate did not match the expected fingerprint.'
        }
    }
    $output = Join-Path $build 'HyperOSPicker.apk'
    Copy-Item -LiteralPath $apk -Destination $output -Force
    Write-Output $output
} finally {
    $resolvedBuild = (Resolve-Path -LiteralPath $build).Path
    $resolvedScratch = (Resolve-Path -LiteralPath $scratch).Path
    if ((Split-Path -Parent $resolvedScratch) -eq $resolvedBuild) {
        Remove-Item -LiteralPath $resolvedScratch -Recurse -Force
    }
}
