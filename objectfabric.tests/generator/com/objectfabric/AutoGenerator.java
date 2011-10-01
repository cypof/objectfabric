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

package com.objectfabric;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import com.objectfabric.misc.SeparateVM;

class AutoGenerator extends Privileged {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ArrayList<String[]> paths = new ArrayList<String[]>();
        File workspace = new File("..");

        for (File project : workspace.listFiles())
            if (project.isDirectory() && !project.getName().equals(".metadata"))
                for (File source : project.listFiles())
                    if (source.isDirectory())
                        findClasses(project, source, paths);

        System.out.println("Found " + paths.size() + " classes");

        for (String[] generator : paths) {
            int index = 0;

            for (;;) {
                String path = generator[0];
                index = path.indexOf(File.separator, index + 1);
                String folder = path.substring(0, index);
                String name = path.substring(index + 1).replace(".class", "").replace(File.separator, ".");

                URL url = new File(folder).toURI().toURL();
                URL[] urls = new URL[] { url };
                ClassLoader loader = new URLClassLoader(urls);

                Class c = null;

                try {
                    c = loader.loadClass(name);
                } catch (Throwable _) {
                }

                if (c != null) {
                    Method main = c.getMethod("main", String[].class);
                    System.out.println("Invoking " + main);

                    // Only way to have current folder set to home is another JVM
                    ArrayList<String> list = new ArrayList<String>();
                    list.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
                    list.add("-cp");
                    String hsql = new File("../objectfabric.examples.sql/libs/hsqldb.jar").getAbsolutePath();
                    list.add(System.getProperty("java.class.path") + ";" + new File(folder).getAbsolutePath() + ";" + hsql);
                    list.add(name);
                    list.add("/Android");
                    list.add("/CSharp");
                    ProcessBuilder builder = new ProcessBuilder(list);
                    builder.directory(new File(generator[1]));
                    Process process = builder.start();
                    SeparateVM.inheritIO(process);
                    int result = process.waitFor();

                    if (result != 0)
                        throw new Exception("Process returned " + result);

                    break;
                }
            }
        }
    }

    private static void findClasses(File project, File folder, ArrayList<String[]> paths) throws Exception {
        for (File file : folder.listFiles()) {
            if (file.isDirectory())
                findClasses(project, file, paths);
            else if (file.getPath().endsWith("generator" + File.separator + "Main.class")) {
                paths.add(new String[] { file.getPath(), project.getAbsolutePath() });
            }
        }
    }
}
