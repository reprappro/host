rem reprap-host -- runs Reprap Java host code with an appropriate classpath

rem Amount of RAM to allow Java VM to use
set REPRAP_RAM_SIZE=1024M

rem set MACHINE=%wmic cpu get AddressWidth%
rem echo %MACHINE%

rem set CLASSPATH=./j3d-org-java3d-all.jar;./j3dutils.jar;./vecmath.jar;./j3dcore.jar;./swing-layout-1.0.4.jar;.

echo %CLASSPATH%

rem set PATH=.\system-dependent\windows\w32

echo %PATH%

rem java -Djava.library.path=.\system-dependent\windows\w32 -cp "./reprap.jar;./j3d-org-java3d-all.jar;./j3dutils.jar;./vecmath.jar;./j3dcore.jar; ./swing-layout-1.0.4.jar;." -Xmx%REPRAP_RAM_SIZE% org/reprap/Main

rem java -Djava.library.path=.\system-dependent\windows\w32;%PATH% -cp "./reprap.jar;./j3d-org-java3d-all.jar;./j3dutils.jar;./vecmath.jar;./j3dcore.jar; ./swing-layout-1.0.4.jar;.;"%CLASSPATH% -Xmx%REPRAP_RAM_SIZE% org/reprap/Main

java -Djava.library.path=.\system-dependent\windows\w32 -cp ".\reprap.jar;.\RXTXcomm.jar;.\j3dcore.jar;.\j3d-org-java3d-all.jar;.\j3dutils.jar;.\swing-layout-1.0.3.jar;.\vecmath.jar;." -Xmx%REPRAP_RAM_SIZE% org/reprap/Main
if ERRORLEVEL 1 pause

