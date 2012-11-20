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

package part02;

import java.util.Random;

import launchfirst.ExamplesServer;

import org.objectfabric.AbstractKeyListener;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.TMap;
import org.objectfabric.Workspace;

/**
 * This sample fetches a map from the {@link ExamplesServer} and updates it randomly.
 * Launch several application instances to see updates propagate in real-time.
 */
@SuppressWarnings("unchecked")
public class RealTime {

    public static void main(String[] args) throws Exception {
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(new NettyURIHandler());

        /*
         * Get map from server.
         */
        final TMap<String, Integer> map = (TMap) workspace.resolve("ws://localhost:8888/map").get();

        /*
         * Add a listener to get notified of updates.
         */
        map.addListener(new AbstractKeyListener<String>() {

            @Override
            public void onPut(String key) {
                System.out.println("/map: " + key + " = " + map.get(key));
            }
        });

        /*
         * Pick a random user name and send updates.
         */
        Random rand = new Random();
        String name = "user " + rand.nextInt(1000);

        for (;;) {
            map.put(name, rand.nextInt(1000));
            Thread.sleep(1000);
        }
    }
}
