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

package site_demo;

import java.text.NumberFormat;

import org.objectfabric.IndexListener;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.TArrayLong;
import org.objectfabric.Workspace;

public class Array {

    /**
     * Code for the project's home page.
     */
    public static void main(String[] args) throws Exception {
        // Like opening a browser
        Workspace w = new JVMWorkspace();

        // Enable network connections
        w.addURIHandler(new NettyURIHandler());

        // Get live array of numbers through WebSocket
        String uri = "ws://test.objectfabric.org/array";
        final TArrayLong a = (TArrayLong) w.open(uri).get();
        final NumberFormat format = NumberFormat.getIntegerInstance();

        // Add a listener on array, called when an element is
        // set to a new value server side
        a.addListener(new IndexListener() {

            @Override
            public void onSet(int i) {
                String n = format.format(a.get(i));

                switch (i) {
                    case 0:
                        System.out.println("World population: " + n);
                        break;
                    case 1:
                        System.out.println("Internet Users: " + n);
                        break;
                }
            }
        });

        Thread.sleep(Long.MAX_VALUE);
    }
}
