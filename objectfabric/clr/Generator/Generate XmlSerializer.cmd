"%ProgramFiles%\Microsoft SDKs\Windows\v7.0A\bin\NETFX 4.0 Tools\sgen" /a:bin\Debug\Generator.exe /keep /parsableerrors /out:bin\Debug
move bin\Debug\*.cs GeneratedXmlSerializer.cs
del bin\Debug\Generator.XmlSerializers.dll
pause