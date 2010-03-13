#!/bin/sh

DIST_ARCHIVE=maple-ide-deps-linux-0018.tar.gz
DIST_URL=http://leaflabs.com/pub

# Have we extracted the dist files yet?
if test ! -d dist/tools/arm
then
  # Have we downloaded the dist files yet? 
  if test ! -f $DIST_ARCHIVE
  then
    echo "Downloading distribution files for linux platform: " $DIST_ARCHIVE
    wget $DIST_URL/$DIST_ARCHIVE
    if test ! -f $DIST_ARCHIVE
    then 
      echo "!!! Problem downloading distribution files; please fetch zip file manually and put it here: "
      echo `pwd`
      exit 1
    fi
  fi
  echo "Extracting distribution files for linux platform: " $DIST_ARCHIVE
  tar --extract --file=maple-ide-deps-linux-0018.tar.gz --ungzip --directory=dist
  if test ! -d dist/tools/arm
  then
    echo "!!! Problem extracting dist file, please fix it."
    exit 1
  fi
fi

### -- SETUP WORK DIR -------------------------------------------

if test -d work
then
  BUILD_PREPROC=false
else
  echo Setting up directories to build for Linux...
  BUILD_PREPROC=true

  mkdir work
  cp -r ../shared/lib work/
  cp -r ../../libraries work/
  cp -r ../shared/tools work/

  cp -r ../../hardware work/

  cp ../../app/lib/antlr.jar work/lib/
  cp ../../app/lib/ecj.jar work/lib/
  cp ../../app/lib/jna.jar work/lib/
  cp ../../app/lib/oro.jar work/lib/
  cp ../../app/lib/RXTXcomm.jar work/lib/

  echo Copying examples...
  cp -r ../shared/examples work/

  echo Extracting reference...
  unzip -q -d work/ ../shared/reference.zip

  cp -r dist/tools work/hardware/

  install -m 755 dist/maple-ide work/maple-ide

  echo NOT extracting full JRE... will attempt to use system-wide version
  #echo Extracting JRE...
  cp -r dist/java work/java

  ARCH=`uname -m`
  if [ $ARCH = "i686" ]
  then
    echo Using 32-bit librxtxSerial.so
    cp dist/lib/librxtxSerial.so work/lib/librxtxSerial.so
  else 
    echo Using 64-bit librxtxSerial.so
    cp dist/lib/librxtxSerial.so.x86_64 work/lib/librxtxSerial.so
  fi
fi

cd ../..


### -- BUILD CORE ----------------------------------------------


echo Building processing.core

cd core

#CLASSPATH="../build/linux/work/java/lib/rt.jar"
#export CLASSPATH

perl preproc.pl
mkdir -p bin
javac -d bin -source 1.5 -target 1.5 \
    src/processing/core/*.java src/processing/xml/*.java
#find bin -name "*~" -exec rm -f {} ';'
rm -f ../build/linux/work/lib/core.jar
cd bin && zip -rq ../../build/linux/work/lib/core.jar \
  processing/core/*.class processing/xml/*.class && cd ..

# back to base processing dir
cd ..


### -- BUILD PDE ------------------------------------------------

cd app

rm -rf ../build/linux/work/classes
mkdir ../build/linux/work/classes

javac -source 1.5 -target 1.5 \
    -classpath ../build/linux/work/lib/core.jar:../build/linux/work/lib/antlr.jar:../build/linux/work/lib/ecj.jar:../build/linux/work/lib/jna.jar:../build/linux/work/lib/oro.jar:../build/linux/work/lib/RXTXcomm.jar:../build/linux/work/java/lib/tools.jar \
    -d ../build/linux/work/classes \
    src/processing/app/*.java \
    src/processing/app/debug/*.java \
    src/processing/app/linux/*.java \
    src/processing/app/preproc/*.java \
    src/processing/app/syntax/*.java \
    src/processing/app/tools/*.java

cd ../build/linux/work/classes
rm -f ../lib/pde.jar
zip -0rq ../lib/pde.jar .
cd ../../../..


echo
echo Done.
