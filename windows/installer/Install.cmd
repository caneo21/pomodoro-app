@echo off
rem Double-click this. Downloaded .ps1 files are blocked by the default execution policy,
rem so this runs Install.ps1 with the policy bypassed for this one script only.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install.ps1"
