rem reprap-host -- runs Reprap Java host code with an appropriate classpath

rem Amount of RAM to allow Java VM to use
set REPRAP_RAM_SIZE=1024M

set MACHINE=%wmic cpu get AddressWidth%
echo %MACHINE%

java -Djava.library.path=%cd%\system-dependent\windows\lib-%MACHINE% -cp "./reprap.jar;./j3d-org-java3d-all.jar;./j3dutils.jar;./vecmath.jar;./j3dcore.jar; ./swing-layout-1.0.4.jar;." -Xmx%REPRAP_RAM_SIZE% org/reprap/Main
if ERRORLEVEL 1 pause
