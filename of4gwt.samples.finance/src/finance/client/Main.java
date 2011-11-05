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

package finance.client;

import of4gwt.misc.Log;
import of4gwt.transports.Client.Callback;
import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

import finance.client.generated.ObjectModel;

/**
 * This client is designed to connect to the Java version of the Images demo. First launch
 * /objectfabric.examples/samples/images/generator/Main.java, then this Web application.
 */
public class Main implements EntryPoint {

    public void onModuleLoad() {
        ObjectModel.register();

        /*
         * Redirect log to web page.
         */
        Log.add(new Log() {

            @Override
            protected void onWrite(String message) {
                RootPanel.get("log").add(new HTML(message));
            }
        });

        HTTPClient client = new HTTPClient("http://localhost:4444");

        client.setCallback(new Callback() {

            @SuppressWarnings("unchecked")
            public void onReceived(Object object) {
            }

            public void onDisconnected(Exception e) {
            }
        });

        // client.connectAsync();

        RootPanel.get("grid").add(new FinanceGrid());
    }
}
