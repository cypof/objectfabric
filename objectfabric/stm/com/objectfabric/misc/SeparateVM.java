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

package com.objectfabric.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Executes a Java application in a separate VM.
 */
public class SeparateVM {

    public static Process start(String className, boolean enableDebugger, boolean waitForDebugger) throws IOException {
        ArrayList<String> list = new ArrayList<String>();
        list.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        list.add("-cp");
        list.add(System.getProperty("java.class.path"));

        if (enableDebugger) {
            list.add("-Xdebug");
            list.add("-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=" + (waitForDebugger ? "y" : "n"));
        }

        list.add(className);
        ProcessBuilder builder = new ProcessBuilder(list);
        builder.directory(new File(System.getProperty("user.dir")));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        inheritIO(process);
        return process;
    }

    public static final void inheritIO(Process process) {
        // Java 7
        // builder.inheritIO();

        final BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));

        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    for (;;) {
                        String line = input.readLine();

                        if (line == null)
                            break;

                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();
    }
}