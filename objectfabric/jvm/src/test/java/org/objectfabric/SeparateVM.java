/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a Java application in a separate VM.
 */
class SeparateVM implements Separate {

    public static final String PROGRESS_KEY = "__progress__";

    private final Process _process;

    private final Runnable _callback;

    private volatile int _progressServer;

    private static AtomicInteger _progressClient = new AtomicInteger();

    private SeparateVM(Process process, Runnable callback) {
        _process = process;
        _callback = callback;
    }

    static SeparateVM start(String className, Collection<String> args, Runnable callback) throws IOException {
        ArrayList<String> list = new ArrayList<String>();
        list.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        list.add("-cp");
        list.add(System.getProperty("java.class.path"));
        list.add("-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=y");
        list.add(SeparateVM.class.getName());
        list.add(className);
        list.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(list);
        builder.directory(new File(System.getProperty("user.dir")));
        builder.redirectErrorStream(true);
        Process process = builder.start();

        SeparateVM vm = new SeparateVM(process, callback);
        vm.track();
        return vm;
    }

    static void main(String[] args) throws Exception {
        Class<?> c = Class.forName(args[0]);
        ArrayList<Class> classes = new ArrayList<Class>();
        ArrayList<Object> values = new ArrayList<Object>();
        classes.add(AtomicInteger.class);
        values.add(_progressClient);

        for (int i = 1; i < args.length; i++) {
            classes.add(int.class);
            values.add(Integer.parseInt(args[i]));
        }

        new Thread() {

            int _last;

            @Override
            public void run() {
                for (;;) {
                    int value = _progressClient.get();

                    if (value != _last) {
                        System.out.println(PROGRESS_KEY + value);
                        _last = value;
                    }

                    try {
                        if (System.in.available() > 0) {
                            byte[] b = new byte[System.in.available()];
                            System.in.read(b);
                            _progressClient.set(Integer.parseInt(new String(b)));
                        }

                        Thread.sleep(1);
                    } catch (Exception e) {
                        Log.write(e);
                        break;
                    }
                }
            }
        }.start();

        Method method = c.getMethod("main", classes.toArray(new Class[classes.size()]));
        method.invoke(null, values.toArray());
    }

    @Override
    public void close() {
        _process.destroy();
    }

    @Override
    public int getProgress() {
        return _progressServer;
    }

    @Override
    public void setProgress(int value) {
        try {
            _process.getOutputStream().write(("" + value).getBytes());
        } catch (IOException e) {
        }
    }

    public int exitValue() {
        return _process.exitValue();
    }

    @Override
    public void waitForProgress(int value) {
        while (_progressServer < value) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public void waitForEnd() {
        try {
            _process.waitFor();
        } catch (InterruptedException e) {
            Log.write(e);
        }
    }

    private final void track() {
        final BufferedReader input = new BufferedReader(new InputStreamReader(_process.getInputStream()));

        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    for (;;) {
                        String line = input.readLine();

                        if (line == null)
                            break;

                        if (line.startsWith(PROGRESS_KEY))
                            _progressServer = Integer.parseInt(line.substring(PROGRESS_KEY.length()));

                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    _process.waitFor();
                } catch (InterruptedException e) {
                    Log.write(e);
                }

                _callback.run();
            }
        };

        thread.start();
    }
}