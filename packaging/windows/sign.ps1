param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Pfx,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$TimestampUrl = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $File)) {
    throw "File not found: $File"
}
if (-not (Test-Path $Pfx)) {
    throw "PFX not found: $Pfx"
}

$signtool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" `
    -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\x64\\' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $signtool) {
    $signtool = (Get-Command signtool.exe -ErrorAction SilentlyContinue).Source
}
if (-not $signtool) {
    throw "signtool.exe not found. Install the Windows SDK."
}

if ($TimestampUrl) {
    & $signtool sign /fd SHA256 /f $Pfx /p $Password `
        /tr $TimestampUrl /td SHA256 $File
} else {
    & $signtool sign /fd SHA256 /f $Pfx /p $Password $File
}

if ($LASTEXITCODE -ne 0) {
    throw "signtool failed with exit code $LASTEXITCODE"
}

Get-AuthenticodeSignature -FilePath $File |
    Format-List Status, StatusMessage, SignerCertificate
