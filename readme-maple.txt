

Disclaimer
------------------------------------------------------------------------------
We make no claims that we "preserve" any design or strategy behind arduino,
which is why this being released as a separate branch of arduino and not as a
patch. Arduino is clearly organized around compiling to avr, and compiling to
arm is more of a hack.


Requirements
------------------------------------------------------------------------------

A number of file archives are not included in this repository and must be 
downloaded and copied in seperately:

avr_tools.zip           ./build/windows/avr_tools.zip
jre.tgz                 ./build/linux/jre.tgz
jre.zip                 ./build/windows/jre.zip
reference.zip           ./build/shared/reference.zip
template.dmg.gz         ./build/macosx/template.dmg.gz
tools-universal.zip     ./build/macosx/dist/tools-universal.zip


Changes from stock arduino codebase
------------------------------------------------------------------------------
 * hardware/maple directory, including bootloader/ and core/
 * app/src/processing/app/Base.java: add getArmBasePath
 * app/src/processing/app/Sketch.java: imports, sizer handling
 * app/src/processing/app/debug/DFUUploader.java: new


