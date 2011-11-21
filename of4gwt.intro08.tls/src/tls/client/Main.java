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

package tls.client;

import of4gwt.misc.Log;
import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Connects to the same server as the Java version of the TLS example (Check
 * objectfabric.examples/intro/part08.tls).
 */
public class Main implements EntryPoint {

    @SuppressWarnings("unchecked")
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
         * Connect to the server through a secure connection.
         */
        final HTTPClient client = new HTTPClient("https://localhost:8443");

        Log.write("Connecting...");

        client.connectAsync(new AsyncCallback<Void>() {

            @Override
            public void onSuccess(Void result) {
                client.send("Done!");
            }

            @Override
            public void onFailure(Throwable caught) {
            }
        });
    }
}
