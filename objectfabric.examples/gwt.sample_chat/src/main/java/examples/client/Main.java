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

package examples.client;

import org.objectfabric.AbstractKeyListener;
import org.objectfabric.AsyncCallback;
import org.objectfabric.GWTWorkspace;
import org.objectfabric.Log;
import org.objectfabric.Resource;
import org.objectfabric.TSet;
import org.objectfabric.WebSocket;
import org.objectfabric.Workspace;

import com.google.gwt.core.client.EntryPoint;

import examples.client.Terminal.Listener;

/**
 * Connects to the chat server (objectfabric.examples-java/src/main/java/sample_chat).
 */
@SuppressWarnings("unchecked")
public class Main implements EntryPoint {

    private String me;

    public void onModuleLoad() {
        /*
         * Redirect log to jQuery.terminal.
         */
        Log.set(Terminal.getInstance());

        Workspace workspace = new GWTWorkspace();
        workspace.addURIHandler(new WebSocket());

        workspace.openAsync("ws://localhost:8888/room1", new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                final TSet<String> messages = (TSet) result.get();

                /*
                 * Display messages that get added to the set.
                 */
                messages.addListener(new AbstractKeyListener<String>() {

                    @Override
                    public void onPut(String key) {
                        Terminal.write(key);
                    }
                });

                Terminal.write("my name? ");

                /*
                 * Listen for typed messages and add them to the set.
                 */
                Terminal.add(new Listener() {

                    @Override
                    public void onLine(String line) {
                        if (me == null) {
                            me = line;
                            messages.add("New user: " + me);
                        } else {
                            messages.add(me + ": " + line);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }
}
