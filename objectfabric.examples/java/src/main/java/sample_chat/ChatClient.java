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

package sample_chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.objectfabric.AbstractKeyListener;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.Resource;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

@SuppressWarnings("unchecked")
public class ChatClient {

    /**
     * This chat sample models chat rooms as a simple set of messages.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("JVM Chat");
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        // Like opening a browser
        Workspace w = new JVMWorkspace();

        // Enables network connections
        w.addURIHandler(new NettyURIHandler());

        // Get a room
        Resource resource = w.resolve("ws://localhost:8888/room1");
        final TSet<String> messages = (TSet) resource.get();

        // A room is a set of messages. Adding a message to a
        // set raises the 'onPut' callback on all clients who
        // share the the same URI

        // Display messages that get added to the set
        messages.addListener(new AbstractKeyListener<String>() {

            @Override
            public void onPut(String key) {
                System.out.println(key);
            }
        });

        // Listen for typed messages and add them to the set
        System.out.print("my name? ");
        String me = console.readLine();
        messages.add("New user: " + me);

        for (;;) {
            String s = console.readLine();
            messages.add(me + ": " + s);
        }
    }
}
