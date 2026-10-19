@echo off
where py >nul 2>nul
if %errorlevel% equ 0 (
  py -3 "%~dp0huawei_notify.py" %*
) else (
  python "%~dp0huawei_notify.py" %*
)
