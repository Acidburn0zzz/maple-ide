#!/bin/sh

# http://dev.processing.org/bugs/show_bug.cgi?id=1179
OSX_VERSION=`sw_vers | grep ProductVersion | awk '{print $2}' | awk '{print substr($0,1,4)}'`
if [ "$OSX_VERSION" = "10.4" ]
then
  echo "This script uses the -X option for cp (to disable resource forks),"
  echo "which is not supported on OS X 10.4. Please either upgrade to 10.5,"
  echo "or modify this script to remove use of the -X switch to continue."
  # and you will also need to remove this error message
  exit
fi

DIST_ARCHIVE=arm-2010q1-188-arm-none-eabi-toolchain-macosx32.tar.gz
DIST_URL=http://static.leaflabs.com/pub/codesourcery

### -- SETUP DIST FILES ----------------------------------------

# Have we extracted the dist files yet?
if test ! -d dist/tools/arm
then
  # Have we downloaded the dist files yet? 
  if test ! -f $DIST_ARCHIVE
  then
    echo "Downloading distribution files for macosx platform: " $DIST_ARCHIVE
    wget $DIST_URL/$DIST_ARCHIVE
    if test ! -f $DIST_ARCHIVE
    then
      echo "!!! Problem downloading distribution files; please fetch zip file manually and put it here: "
      echo `pwd`
      exit 1
    fi
  fi
  echo "Extracting distribution files for macosx platform: " $DIST_ARCHIVE
  tar --extract --file=$DIST_ARCHIVE --ungzip --directory=dist/tools
  if test ! -d dist/tools/arm
  then
    echo "!!! Problem extracting dist file, please fix it."
    exit 1
  fi
fi

### -- SETUP WORK DIR -------------------------------------------

RESOURCES=`pwd`/work/Arduino.app/Contents/Resources/Java
#echo $RESOURCES
#exit

if test -d work
then
  BUILD_PREPROC=false
else
  echo Setting up directories to build under Mac OS X
  BUILD_PREPROC=true

  mkdir work

  # to have a copy of this guy around for messing with
  echo Copying Arduino.app...
  #cp -a dist/Arduino.app work/   # #@$(* bsd switches
  #/sw/bin/cp -a dist/Arduino.app work/
  cp -pRX dist/Arduino.app work/
  # cvs doesn't seem to want to honor the +x bit 
  chmod +x work/Arduino.app/Contents/MacOS/JavaApplicationStub

  cp -rX ../shared/lib "$RESOURCES/"
  cp -rX ../../libraries "$RESOURCES/"
  cp -rX ../shared/tools "$RESOURCES/"

  cp -rX ../../hardware "$RESOURCES/"

  cp -X ../../app/lib/antlr.jar "$RESOURCES/"
  cp -X ../../app/lib/ecj.jar "$RESOURCES/"
  cp -X ../../app/lib/jna.jar "$RESOURCES/"
  cp -X ../../app/lib/oro.jar "$RESOURCES/"
  cp -X ../../app/lib/RXTXcomm.jar "$RESOURCES/"
  cp -X ../../README-dist "$RESOURCES/README.txt"
  cp -X ../../readme-arduino.txt "$RESOURCES/"

  echo Copying examples...
  cp -rX ../shared/examples "$RESOURCES/"

  #echo Extracting reference...
  #unzip -q -d "$RESOURCES/" ../shared/reference.zip
  echo Copying reference...
  cp -r ../shared/reference "$RESOURCES/"

  echo Copying arm tools...
  cp -rX dist/tools/arm "$RESOURCES/hardware/tools/" 

  echo Copying dfu-util...
  cp work/Arduino.app/Contents/Resources/Java/hardware/tools/dfu-util work/Arduino.app/Contents/Resources/Java/hardware/tools/arm/bin
  mkdir -p  work/Arduino.app/Contents/Resources/Java/hardware/tools/arm/Resources
  cp work/Arduino.app/Contents/Resources/Java/hardware/tools/ work/Arduino.app/Contents/Resources/Java/hardware/tools/arm/Resources
fi


### -- START BUILDING -------------------------------------------

# move to root 'processing' directory
cd ../..


### -- BUILD CORE ----------------------------------------------

echo Building processing.core...

cd core

#CLASSPATH=/System/Library/Frameworks/JavaVM.framework/Classes/classes.jar:/System/Library/Frameworks/JavaVM.framework/Classes/ui.jar:/System/Library/Java/Extensions/QTJava.zip
#export CLASSPATH

perl preproc.pl

mkdir -p bin
javac -source 1.5 -target 1.5 -d bin \
  src/processing/core/*.java \
  src/processing/xml/*.java

rm -f "$RESOURCES/core.jar"

cd bin && \
  zip -rq "$RESOURCES/core.jar" \
  processing/core/*.class \
  processing/xml/*.class \
  && cd ..

# head back to "processing/app"
cd ../app



### -- BUILD PDE ------------------------------------------------

echo Building the PDE...

# For some reason, javac really wants this folder to exist beforehand.
rm -rf ../build/macosx/work/classes
mkdir ../build/macosx/work/classes
# Intentionally keeping this separate from the 'bin' folder
# used by eclipse so that they don't cause conflicts.

javac \
    -Xlint:deprecation \
    -source 1.5 -target 1.5 \
    -classpath "$RESOURCES/core.jar:$RESOURCES/antlr.jar:$RESOURCES/ecj.jar:$RESOURCES/jna.jar:$RESOURCES/oro.jar:$RESOURCES/RXTXcomm.jar" \
    -d ../build/macosx/work/classes \
    src/processing/app/*.java \
    src/processing/app/debug/*.java \
    src/processing/app/macosx/*.java \
    src/processing/app/preproc/*.java \
    src/processing/app/syntax/*.java \
    src/processing/app/tools/*.java

cd ../build/macosx/work/classes
rm -f "$RESOURCES/pde.jar"
zip -0rq "$RESOURCES/pde.jar" .
cd ../..

# get updated core.jar and pde.jar; also antlr.jar and others
#mkdir -p work/Arduino.app/Contents/Resources/Java/
#cp work/lib/*.jar work/Arduino.app/Contents/Resources/Java/


echo
echo Done.
