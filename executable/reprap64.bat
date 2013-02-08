rem reprap-host -- runs Reprap Java host code with an appropriate classpath

setlocal
rem Amount of RAM to allow Java VM to use
set REPRAP_RAM_SIZE=1024M

java -Djava.library.path=.\system-dependent\windows\w64 -cp ".\reprap.jar;.\RXTXcomm.jar;.\j3dcore.jar;.\j3d-org-java3d-all.jar;.\j3dutils.jar;.\swing-layout-1.0.4.jar;.\vecmath.jar;." -Xmx%REPRAP_RAM_SIZE% org/reprap/Main
if ERRORLEVEL 1 pause
endlocal
