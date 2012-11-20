@echo off
cd ..\lib
..\ikvm\ikvmstub mscorlib
..\ikvm\ikvmstub System
..\ikvm\ikvmstub System.Numerics
..\ikvm\ikvmstub ..\ikvm\IKVM.OpenJDK.Core.dll
pause