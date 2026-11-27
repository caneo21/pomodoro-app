# Installs Pomodoro for Windows from the files in this folder.
# Needs, next to this script:
#   PomodoroWindows.cer
#   PomodoroWindows-<version>-<x64|arm64>.msix
#   WindowsAppRuntime-<x64|arm64>.msix   (the Windows App Runtime the app is built on)
# The first install trusts the certificate (one admin prompt); later updates install directly.
param([switch]$ImportCertOnly)

$ErrorActionPreference = 'Stop'

function Exit-WithMessage([string]$message, [int]$code) {
    Write-Host ''
    Write-Host $message
    Read-Host '엔터를 누르면 창이 닫혀요' | Out-Null
    exit $code
}

$here = $PSScriptRoot
$cerPath = Join-Path $here 'PomodoroWindows.cer'

# Elevated helper (hidden window, so no prompts): only trusts the certificate. The app itself is
# installed by the non-elevated run below, so it lands in the signed-in user's account even when
# someone else's admin credentials were typed into the prompt.
if ($ImportCertOnly) {
    try {
        Import-Certificate -FilePath $cerPath -CertStoreLocation 'Cert:\LocalMachine\TrustedPeople' | Out-Null
        exit 0
    }
    catch {
        exit 1
    }
}

try {
    $arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'x64' }

    $msix = Get-ChildItem -Path $here -Filter "PomodoroWindows-*-$arch.msix" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $msix) {
        Exit-WithMessage "이 PC에 맞는 설치 파일(.msix)을 같은 폴더에 넣어주세요. (필요한 파일: PomodoroWindows-*-$arch.msix)" 1
    }
    # Closing the window only hides Pomodoro to the tray, and Windows can't replace a running app
    # (Add-AppxPackage fails with "package in use"), so ask the user to quit it first.
    if (Get-Process -Name 'PomodoroWindows' -ErrorAction SilentlyContinue) {
        Exit-WithMessage 'Pomodoro가 실행 중이에요. 작업 표시줄 오른쪽 트레이의 Pomodoro 아이콘을 우클릭해서 종료한 뒤 Install.cmd를 다시 실행해주세요.' 1
    }
    if (-not (Test-Path $cerPath)) {
        Exit-WithMessage 'PomodoroWindows.cer 파일을 같은 폴더에 넣어주세요.' 1
    }

    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 $cerPath
    $isTrusted = { [bool](Get-ChildItem 'Cert:\LocalMachine\TrustedPeople' | Where-Object { $_.Thumbprint -eq $cert.Thumbprint }) }

    if (-not (& $isTrusted)) {
        Write-Host '처음 설치라서 인증서를 등록해야 해요. 관리자 권한 창에서 "예"를 눌러주세요.'
        try {
            Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList `
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-ImportCertOnly'
        }
        catch {
            Exit-WithMessage '관리자 권한이 없어서 인증서를 등록하지 못했어요. 다시 실행해서 "예"를 눌러주세요.' 1
        }
        if (-not (& $isTrusted)) {
            Exit-WithMessage '인증서를 등록하지 못했어요. 다시 실행해서 "예"를 눌러주세요.' 1
        }
        Write-Host '인증서를 등록했어요.'
    }

    Write-Host "$($msix.Name) 설치 중..."
    $runtime = Join-Path $here "WindowsAppRuntime-$arch.msix"
    if (Test-Path $runtime) {
        # Installs (or keeps) the Windows App Runtime the app depends on, in the same step.
        Add-AppxPackage -Path $msix.FullName -DependencyPath $runtime -ForceUpdateFromAnyVersion
    }
    else {
        Add-AppxPackage -Path $msix.FullName -ForceUpdateFromAnyVersion
    }
    Exit-WithMessage '설치가 끝났어요. 시작 메뉴에서 Pomodoro를 실행하세요.' 0
}
catch {
    Exit-WithMessage "설치하지 못했어요: $($_.Exception.Message)" 1
}
