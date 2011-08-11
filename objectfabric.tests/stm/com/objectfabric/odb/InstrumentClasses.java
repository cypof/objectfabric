/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.odb;

public class InstrumentClasses {

    // private static int nFiles = 0;
    //
    // public static void main(String[] args) throws Exception {
    // Debugify.initialize();
    //
    // instrument(new File("classes"));
    //
    // System.out.println("Debugified " + nFiles + " files.");
    // }
    //
    // public static void instrument(File file) throws Exception {
    // if (file.isDirectory()) {
    // for (File child : file.listFiles())
    // instrument(child);
    // } else if (file.getName().endsWith(".class")) {
    // JavaClass javaClass = new ClassParser(file.getAbsolutePath()).parse();
    // JavaClass newJC = Debugify.debugifyClass(javaClass, file.getAbsolutePath());
    // newJC.dump(file.getAbsolutePath().replace("classes", "temp"));
    //
    // nFiles++;
    // }
    // }
}
