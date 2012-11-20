cd bin\Debug
..\..\..\ikvm\ikvmstub.exe Shared1.dll
copy Shared1.dll ..\..\..\lib
copy Shared1.pdb ..\..\..\lib
copy Shared1.jar ..\..\..\lib

IF "%1"=="-nopause" goto nopause
pause

:nopause
