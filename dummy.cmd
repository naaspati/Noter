@echo off
setlocal

cd %~dp0
del dummy.jbook
echo copy "C:\Users\Sameer\Documents\MEGAsync\Mega\jbooks\C.jbook" dummy.jbook
copy "C:\Users\Sameer\Documents\MEGAsync\Mega\jbooks\C.jbook" dummy.jbook

pause