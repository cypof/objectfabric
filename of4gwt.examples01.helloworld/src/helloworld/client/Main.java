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

package helloworld.client;

import of4gwt.misc.Log;
import of4gwt.transports.Client.Callback;
import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Connects to the same server as the Java version of the Hello World example (Check
 * objectfabric.examples/part01.helloworld).
 */
public class Main implements EntryPoint {

    public void onModuleLoad() {
        /*
         * Redirect log to web page.
         */
        Log.add(new Log() {

            @Override
            protected void onWrite(String message) {
                RootPanel.get("log").add(new HTML(message));
            }
        });

        /*
         * Connect to the server through a Comet connection.
         */
        final HTTPClient client = new HTTPClient("http://localhost:8080");

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                Log.write("Received: " + object);

                // Send back the response
                client.send(object + " World!");
            }

            public void onDisconnected(Exception e) {
            }
        });

        Log.write("Connecting...");
        client.connectAsync();
    }
}
