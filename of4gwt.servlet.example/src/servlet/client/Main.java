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

package servlet.client;

import of4gwt.ServletClient;
import of4gwt.misc.Log;
import of4gwt.transports.Client.Callback;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * This example connects to a server using polling over GWT-RPC instead of Comet. It is
 * compatible with any servlet container like Tomcat or Jetty. Check war/WEB-INF/web.xml
 * to see how the GWT servlet is configured.
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

        final ServletClient client = new ServletClient();

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                Log.write("Received: " + object);
                client.send("Blah");
            }

            public void onDisconnected(Exception e) {
            }
        });

        Log.write("Connecting...");
        client.connectAsync();
    }
}
