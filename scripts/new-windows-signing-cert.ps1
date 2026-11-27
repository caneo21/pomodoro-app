<#
.SYNOPSIS
    Creates the self-signed certificate that signs the Windows MSIX releases. Run once.

.DESCRIPTION
    Writes PomodoroWindows.pfx (private, password-protected) and PomodoroWindows.cer (public) to
    -OutDir, then prints the PFX as base64 for the WINDOWS_CERT_PFX_BASE64 GitHub secret.

    Reuse the same certificate for every release: a new one means everyone has to trust it again.
    Back up the .pfx somewhere off this PC. Both files are git-ignored (keystore/*.pfx, *.cer).

.EXAMPLE
    powershell -File scripts/new-windows-signing-cert.ps1
#>
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\keystore'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# Must equal the Publisher in windows/PomodoroWindows/Package.appxmanifest, or MSIX signing fails.
$subject = 'CN=PomodoroWindowsDev'

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$OutDir = (Resolve-Path $OutDir).Path
$pfxPath = Join-Path $OutDir 'PomodoroWindows.pfx'
$cerPath = Join-Path $OutDir 'PomodoroWindows.cer'

if ((Test-Path $pfxPath) -and -not $Force) {
    Write-Error "$pfxPath already exists. Releases must keep using the same certificate; pass -Force only if you really want a new one (everyone will have to trust it again)."
}

$password = Read-Host -AsSecureString 'Password for the new .pfx (you will add it as WINDOWS_CERT_PASSWORD)'

$cert = New-SelfSignedCertificate `
    -Type Custom `
    -Subject $subject `
    -KeyUsage DigitalSignature `
    -FriendlyName 'Pomodoro Windows Signing' `
    -CertStoreLocation 'Cert:\CurrentUser\My' `
    -TextExtension @('2.5.29.37={text}1.3.6.1.5.5.7.3.3', '2.5.29.19={text}') `
    -NotAfter (Get-Date).AddYears(5)

try {
    Export-PfxCertificate -Cert $cert -FilePath $pfxPath -Password $password | Out-Null
    Export-Certificate -Cert $cert -FilePath $cerPath | Out-Null
}
finally {
    # The .pfx file is the copy that matters; don't leave a second private key in the user store.
    Remove-Item -Path "Cert:\CurrentUser\My\$($cert.Thumbprint)"
}

Write-Host "Created:`n  $pfxPath`n  $cerPath"
Write-Host "Thumbprint: $($cert.Thumbprint)"
Write-Host ''
Write-Host 'Base64 for the WINDOWS_CERT_PFX_BASE64 secret (also copied to the clipboard):'
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($pfxPath))
$base64 | Set-Clipboard
Write-Host $base64
