/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*

  Modified version of Compiler.java, which is...

  Part of the Processing project - http://processing.org

  Copyright (c) 2004-08 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.debug;

import processing.app.debug.Compiler;
import processing.app.Base;
import processing.app.Preferences;
import processing.app.Sketch;
import processing.app.SketchCode;
import processing.core.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;


public class ArmCompiler extends Compiler {

  public ArmCompiler() { }

  private boolean messagesNonError = false; // THIS IS SUCH A HACK.

  /**
   * Compile for ARM with make
   *
   * @param sketch Sketch object to be compiled.
   * @param buildPath Where the temporary files live and will be built from.
   * @param primaryClassName the name of the combined sketch file w/ extension
   * @return true if successful.
   * @throws RunnerException Only if there's a problem. Only then.
   */
  public boolean compile(Sketch sketch,
                         String buildPath,
                         String primaryClassName,
                         boolean verbose) throws RunnerException {
    this.sketch = sketch;
    this.buildPath = buildPath;
    this.primaryClassName = primaryClassName;
    this.verbose = verbose;

    // the pms object isn't used for anything but storage
    MessageStream pms = new MessageStream(this);

    String armBasePath = Base.getArmBasePath();
    Map<String, String> boardPreferences;
    try {
        boardPreferences = Base.getBoardPreferences();
    } catch (NullPointerException npe) {
        Base.showWarning("No board selected", "please choose one from the Tools menu.",npe);
        return false;
    }
    String core = boardPreferences.get("build.core");
    String corePath;

    if (core.indexOf(':') == -1) {
      Target t = Base.getTarget();
      File coreFolder = new File(new File(t.getFolder(), "cores"), core);
      corePath = coreFolder.getAbsolutePath();
    } else {
      Target t = Base.targetsTable.get(core.substring(0, core.indexOf(':')));
      File coresFolder = new File(t.getFolder(), "cores");
      File coreFolder = new File(coresFolder, core.substring(core.indexOf(':') + 1));
      corePath = coreFolder.getAbsolutePath();
    }

    List<File> objectFiles = new ArrayList<File>();

    List includePaths = new ArrayList();
    includePaths.add(corePath);

    String runtimeLibraryName = buildPath + File.separator + "core.a";

    // 1. compile the target (core), outputting .o files to <buildPath> and
    // then collecting them into the core.a library file.
    System.out.print("\tCompiling core...\n");

    objectFiles.addAll(
      compileFiles(armBasePath, buildPath, includePaths,
                   findFilesInPath(corePath, "S", true),
                   findFilesInPath(corePath, "c", true),
                   findFilesInPath(corePath, "cpp", true),
                   boardPreferences));
    
    /*
    List baseCommandAR = new ArrayList(Arrays.asList(new String[] {
      avrBasePath + "avr-ar",
      "rcs",
      runtimeLibraryName
    }));

    for(File file : coreObjectFiles) {
      List commandAR = new ArrayList(baseCommandAR);
      commandAR.add(file.getAbsolutePath());
      execAsynchronously(commandAR);
    }
    */

    // 2. compile the libraries, outputting .o files to: <buildPath>/<library>/

    // use library directories as include paths for all libraries
    for (File file : sketch.getImportedLibraries()) {
      includePaths.add(file.getPath());
    }

    System.out.print("\tCompiling any libs...\n");
    for (File libraryFolder : sketch.getImportedLibraries()) {
      File outputFolder = new File(buildPath, libraryFolder.getName());
      File utilityFolder = new File(libraryFolder, "utility");
      createFolder(outputFolder);
      // this library can use includes in its utility/ folder
      includePaths.add(utilityFolder.getAbsolutePath());
      objectFiles.addAll(
        compileFiles(armBasePath, outputFolder.getAbsolutePath(), includePaths,
                     findFilesInFolder(libraryFolder, "S", false),
                     findFilesInFolder(libraryFolder, "c", false),
                     findFilesInFolder(libraryFolder, "cpp", false),
                     boardPreferences));
      outputFolder = new File(outputFolder, "utility");
      createFolder(outputFolder);
      objectFiles.addAll(
        compileFiles(armBasePath, outputFolder.getAbsolutePath(), includePaths,
                     findFilesInFolder(utilityFolder, "S", false),
                     findFilesInFolder(utilityFolder, "c", false),
                     findFilesInFolder(utilityFolder, "cpp", false),
                     boardPreferences));
      // other libraries should not see this library's utility/ folder
      includePaths.remove(includePaths.size() - 1);
    }

    // 3. compile the sketch (already in the buildPath)

    System.out.print("\tCompiling the sketch...\n");
    objectFiles.addAll(
      compileFiles(armBasePath, buildPath, includePaths,
                   findFilesInPath(buildPath, "S", false),
                   findFilesInPath(buildPath, "c", false),
                   findFilesInPath(buildPath, "cpp", false),
                   boardPreferences));

    // 4. link it all together into the .elf file
    //System.out.print("Linking compiled \n");
    String linkerInclude = "-L"+corePath;
    String linkerScript  = "-T"+corePath+File.separator+boardPreferences.get("build.linker");
    //String mapOut        = "-Map="+buildPath+File.separator+primaryClassName+".map";
    List baseCommandLinker = new ArrayList(Arrays.asList(new String[] {
      armBasePath + "arm-none-eabi-gcc",
      linkerScript,
      linkerInclude,
      "-mcpu=cortex-m3",
      "-mthumb",
      "-Xlinker",
      //      mapOut,
      "--gc-sections",
      "--print-gc-sections",
      "--march=armv7-m",
      "-Wall",
      "-o",
      buildPath + File.separator + primaryClassName + ".out"
    }));

    for (File file : objectFiles) {
      //System.out.println(file);
      baseCommandLinker.add(file.getAbsolutePath());
    }

    baseCommandLinker.add("-L" + buildPath);
    System.out.print("\trunning linker asynchronously...\n");
    execAsynchronously(baseCommandLinker);

    List commandObjCopy = new ArrayList(Arrays.asList(new String[] {
      armBasePath + "arm-none-eabi-objcopy",
      "-v",
      "-Obinary",
      buildPath + File.separator + primaryClassName + ".out",
      buildPath + File.separator + primaryClassName + ".bin"
    }));
    
    System.out.print("\trunning obj copy asynchronously...\n");
    execAsynchronously(commandObjCopy);

    List commandSize = new ArrayList(Arrays.asList(new String[] {
      armBasePath + "arm-none-eabi-size",
      "--target=binary",
      "-A",
      buildPath + File.separator + primaryClassName + ".bin"
    }));
    
    System.out.print("\trunning size asynchronously...\n");
    messagesNonError = true;
    System.out.println();
    execAsynchronously(commandSize);
    messagesNonError = false;

    return true;
  }

  private List<File> compileFiles(String avrBasePath,
                                  String buildPath, List<File> includePaths,
                                  List<File> sSources, 
                                  List<File> cSources, List<File> cppSources,
                                  Map<String, String> boardPreferences)
    throws RunnerException {

    List<File> objectPaths = new ArrayList<File>();
    
    for (File file : sSources) {
      String objectPath = buildPath + File.separator + file.getName() + ".o";
      objectPaths.add(new File(objectPath));
      execAsynchronously(getCommandCompilerS(avrBasePath, includePaths,
                                             file.getAbsolutePath(),
                                             objectPath,
                                             boardPreferences));
    }
 		
    for (File file : cSources) {
        String objectPath = buildPath + File.separator + file.getName() + ".o";
        objectPaths.add(new File(objectPath));
        execAsynchronously(getCommandCompilerC(avrBasePath, includePaths,
                                               file.getAbsolutePath(),
                                               objectPath,
                                               boardPreferences));
    }

    for (File file : cppSources) {
        String objectPath = buildPath + File.separator + file.getName() + ".o";
        objectPaths.add(new File(objectPath));
        execAsynchronously(getCommandCompilerCPP(avrBasePath, includePaths,
                                                 file.getAbsolutePath(),
                                                 objectPath,
                                                 boardPreferences));
    }
    
    return objectPaths;
  }


  /////////////////////////////////////////////////////////////////////////////

  static private List getCommandCompilerS(String armBasePath, List includePaths,
    String sourceName, String objectName, Map<String, String> boardPreferences) {

    List baseCommandCompiler = new ArrayList(Arrays.asList(new String[] {
      armBasePath + "arm-none-eabi-gcc",
      "-mthumb",
      //"-g",
      "-mcpu=cortex-m3", 
      "-assembler-with-cpp",
      "-Wall",
      "-Os",
      //      "-g",
      "-DF_CPU=" + boardPreferences.get("build.f_cpu"),
      "-D"+ boardPreferences.get("build.vect"),
      "-DBOARD_"+ boardPreferences.get("build.board"),
      "-DMCU_"+ boardPreferences.get("build.mcu"),
      "-DARDUINO=" + Base.REVISION,
    }));

    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompiler.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompiler.add(sourceName);
    baseCommandCompiler.add("-o"+ objectName);

    return baseCommandCompiler;
  }

  
  static private List getCommandCompilerC(String armBasePath, List includePaths,
    String sourceName, String objectName, Map<String, String> boardPreferences) {

    List baseCommandCompiler = new ArrayList(Arrays.asList(new String[] {
          armBasePath + "arm-none-eabi-gcc"}));

    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompiler.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompiler.add("-c"); 
    baseCommandCompiler.add("-Os");
    baseCommandCompiler.add("-g");
    baseCommandCompiler.add("-mcpu=cortex-m3");
    baseCommandCompiler.add("-mthumb");
    baseCommandCompiler.add("-march=armv7-m");
    baseCommandCompiler.add("-nostdlib");
    baseCommandCompiler.add("-ffunction-sections");
    baseCommandCompiler.add("-fdata-sections");
    baseCommandCompiler.add("-Wl,--gc-sections");
    baseCommandCompiler.add("-DF_CPU=" + boardPreferences.get("build.f_cpu"));
    baseCommandCompiler.add("-D" + boardPreferences.get("build.vect"));
    baseCommandCompiler.add("-DBOARD_" + boardPreferences.get("build.board"));
    baseCommandCompiler.add("-DMCU_" + boardPreferences.get("build.mcu"));
    baseCommandCompiler.add("-DARDUINO=" + Base.REVISION);
    baseCommandCompiler.add("-c");

    baseCommandCompiler.add(sourceName);
    baseCommandCompiler.add("-o"+ objectName);

    return baseCommandCompiler;
  }
	
  static private List getCommandCompilerCPP(String armBasePath,
    List includePaths, String sourceName, String objectName,
    Map<String, String> boardPreferences) {
    
    List baseCommandCompilerCPP = new ArrayList(Arrays.asList(new String[] {
          armBasePath + "arm-none-eabi-gcc"}));

    for (int i = 0; i < includePaths.size(); i++) {
      baseCommandCompilerCPP.add("-I" + (String) includePaths.get(i));
    }

    baseCommandCompilerCPP.add("-c"); 
    baseCommandCompilerCPP.add("-Os");
    baseCommandCompilerCPP.add("-g");
    baseCommandCompilerCPP.add("-mcpu=cortex-m3");
    baseCommandCompilerCPP.add("-mthumb");
    baseCommandCompilerCPP.add("-march=armv7-m");
    baseCommandCompilerCPP.add("-nostdlib");
    baseCommandCompilerCPP.add("-ffunction-sections");
    baseCommandCompilerCPP.add("-fdata-sections");
    baseCommandCompilerCPP.add("-Wl,--gc-sections");
    baseCommandCompilerCPP.add("-DF_CPU=" + boardPreferences.get("build.f_cpu"));
    baseCommandCompilerCPP.add("-D" + boardPreferences.get("build.vect"));
    baseCommandCompilerCPP.add("-DBOARD_"+ boardPreferences.get("build.board"));
    baseCommandCompilerCPP.add("-DMCU_"+ boardPreferences.get("build.mcu"));
    baseCommandCompilerCPP.add("-DARDUINO=" + Base.REVISION);
    baseCommandCompilerCPP.add("-fno-rtti");
    baseCommandCompilerCPP.add("-fno-exceptions");
    baseCommandCompilerCPP.add("-Wall");
    baseCommandCompilerCPP.add("-c");

    baseCommandCompilerCPP.add(sourceName);
    baseCommandCompilerCPP.add("-o"+ objectName);

    return baseCommandCompilerCPP;
  }

  /**
   * Part of the MessageConsumer interface, this is called
   * whenever a piece (usually a line) of error message is spewed
   * out from the compiler. The errors are parsed for their contents
   * and line number, which is then reported back to Editor.
   */
  public void message(String s) {
    // This receives messages as full lines, so a newline needs
    // to be added as they're printed to the console.
    //System.err.print(s);

    // SUCH A HACK
    if (messagesNonError) {
      System.out.print(s);
      return;
    }

    // ignore cautions
    if (s.indexOf("warning") != -1) return;

    // ignore this line; the real error is on the next one
    if (s.indexOf("In file included from") != -1) return;

    // ignore obj copy
    if (s.indexOf("copy from ") != -1) return;

    // jikes always uses a forward slash character as its separator,
    // so replace any platform-specific separator characters before
    // attemping to compare
    //
    //String buildPathSubst = buildPath.replace(File.separatorChar, '/') + "/";
    String buildPathSubst =
      buildPath.replace(File.separatorChar,File.separatorChar) +
      File.separatorChar;

    String partialTempPath = null;
    int partialStartIndex = -1; //s.indexOf(partialTempPath);
    int fileIndex = -1;  // use this to build a better exception

    // check the main sketch file first.
    partialTempPath = buildPathSubst + primaryClassName;
    partialStartIndex = s.indexOf(partialTempPath);

    if (partialStartIndex != -1) {
      fileIndex = 0;
    } else {
      // wasn't there, check the other (non-pde) files in the sketch.
      // iterate through the project files to see who's causing the trouble
      for (int i = 0; i < sketch.getCodeCount(); i++) {
        if (sketch.getCode(i).isExtension("pde")) continue;

        partialTempPath = buildPathSubst + sketch.getCode(i).getFileName();
        //System.out.println(partialTempPath);
        partialStartIndex = s.indexOf(partialTempPath);
        if (partialStartIndex != -1) {
          fileIndex = i;
          //System.out.println("fileIndex is " + fileIndex);
          break;
        }
      }
      //+ className + ".java";
    }

    // if the partial temp path appears in the error message...
    //
    //int partialStartIndex = s.indexOf(partialTempPath);
    if (partialStartIndex != -1) {

      // skip past the path and parse the int after the first colon
      //
      String s1 = s.substring(partialStartIndex +
                              partialTempPath.length() + 1);
      //System.out.println(s1);
      int colon = s1.indexOf(':');

      if (s1.indexOf("In function") != -1 || colon == -1) {
        System.err.print(s1);
        //firstErrorFound = true;
        return;
      }

      int lineNumber;
      try {
        lineNumber = Integer.parseInt(s1.substring(0, colon));
      } catch (NumberFormatException e) {
        System.err.print(s1);
        return;
      }
      
      //System.out.println("pde / line number: " + lineNumber);
      
      if (fileIndex == 0) {  // main class, figure out which tab
        for (int i = 1; i < sketch.getCodeCount(); i++) {
          if (sketch.getCode(i).isExtension("pde")) {
            //System.out.println("preprocOffset "+ sketch.getCode(i).getPreprocOffset());
            if (sketch.getCode(i).getPreprocOffset() < lineNumber) {
              fileIndex = i;
              //System.out.println("i'm thinkin file " + i);
            }
          }
        }
        // OLD to do: DAM: if the lineNumber is less than sketch.getCode(0).getPreprocOffset()
        // we shouldn't subtract anything from it, as the error is above the
        // location where the function prototypes and #include "WProgram.h"
        // were inserted.
        lineNumber -= sketch.getCode(fileIndex).getPreprocOffset();
      }

      //String s2 = s1.substring(colon + 2);
      int err = s1.indexOf(":");
      if (err != -1) {

        // if the first error has already been found, then this must be
        // (at least) the second error found
        if (firstErrorFound) {
          secondErrorFound = true;
          return;
        }

        // if executing at this point, this is *at least* the first error
        firstErrorFound = true;

        err += ":".length();
        String description = s1.substring(err);
        description = description.trim();
        System.err.print(description);

        //System.out.println("description = " + description);
        //System.out.println("creating exception " + exception);
        exception = new RunnerException(description, fileIndex, lineNumber-1, -1, false);

        // NOTE!! major change here, this exception will be queued
        // here to be thrown by the compile() function
        //editor.error(exception);

      } else {
        System.err.println("i suck: " + s);
      }

    } else {
      // this isn't the start of an error line, so don't attempt to parse
      // a line number out of it.

      // if the second error hasn't been discovered yet, these lines
      // are probably associated with the first error message,
      // which is already in the status bar, and are likely to be
      // of interest to the user, so spit them to the console.
//
      if (!secondErrorFound) {
        System.err.println(s);
      }
    }
  }

}

