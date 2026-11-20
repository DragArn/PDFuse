param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$Subject = "CN=PDFuse Local Test",
    [string]$Password = "pdfuse-local",
    [string]$OutDir = "$env:TEMP\pdfuse-signing",
    [string]$TimestampUrl = ""
)

$ErrorActionPreference = "Stop"

Write-Warning "Local test only: this creates a self-signed certificate and adds it to your user trust stores."
Write-Warning "Do not distribute this certificate or use it for production builds."

if (-not (Test-Path $File)) {
    throw "File not found: $File"
}

New-Item -ItemType Directory -Force $OutDir | Out-Null
$pfx = Join-Path $OutDir "pdfuse-local.pfx"
$cer = Join-Path $OutDir "pdfuse-local.cer"

$certificate = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject $Subject `
    -KeyUsage DigitalSignature `
    -KeyExportPolicy Exportable `
    -HashAlgorithm SHA256 `
    -FriendlyName "PDFuse local test" `
    -CertStoreLocation "Cert:\CurrentUser\My" `
    -NotAfter (Get-Date).AddYears(3)

$securePassword = ConvertTo-SecureString -String $Password -Force -AsPlainText
Export-PfxCertificate -Cert $certificate -FilePath $pfx -Password $securePassword | Out-Null
Export-Certificate -Cert $certificate -FilePath $cer | Out-Null

Import-Certificate -FilePath $cer `
    -CertStoreLocation "Cert:\CurrentUser\TrustedPublisher" | Out-Null
Import-Certificate -FilePath $cer `
    -CertStoreLocation "Cert:\CurrentUser\Root" | Out-Null

& "$PSScriptRoot\sign.ps1" -File $File -Pfx $pfx -Password $Password `
    -TimestampUrl $TimestampUrl

Write-Host ""
Write-Host "Local signing done. The warning may persist until SmartScreen reputation exists."
