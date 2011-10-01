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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformConsole;

public class Misc extends TestsHelper {

    public static void main(String[] args) throws Exception {
        Thread thread = new Thread() {

            @Override
            public void run() {
                throw new OutOfMemoryError();
            }
        };

        thread.start();
        PlatformConsole.readLine();

        ArrayList<Runnable> list = new ArrayList<Runnable>();
        HashSet<Runnable> set = new HashSet<Runnable>();

        Runnable r = new Runnable() {

            public void run() {
                System.out.println("blah");
            }
        };

        for (int i = 0; i < 2; i++) {

            list.add(r);
            set.add(r);
        }

        //

        Log.write("bls");

        int total = 0;

        for (int i = 0; i < (int) 1e6; i++)
            total += i / 1024;

        Random rand = new Random();
        int count = (int) 1e7;
        long start = System.nanoTime();

        for (int i = 0; i < count; i++)
            // total += i / 1024;
            // total += i >> 10;
            total += rand.nextInt(100);
        NumberFormat format = NumberFormat.getNumberInstance();
        System.out.println(total);
        double ms = (System.nanoTime() - start) / 1e6;
        System.out.println("Perf test : " + format.format(ms) + " ms  (" + format.format(count / (ms / 1000)) + " per second)");
    }
}
