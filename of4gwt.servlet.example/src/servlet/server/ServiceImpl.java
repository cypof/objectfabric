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

package servlet.server;

import of4gwt.misc.Log;

import com.objectfabric.ServletServiceImpl;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.polling.PollingServer;

public class ServiceImpl extends ServletServiceImpl {

    private static PollingServer<ServletSession> _server;

    static {
        _server = new PollingServer<ServletSession>();

        _server.setCallback(new Callback<ServletSession>() {

            @Override
            public void onConnection(ServletSession session) {
                Log.write("Connection (" + session.getHttpSession().getId() + ")");
                session.send("Blah");
            }

            @Override
            public void onDisconnection(ServletSession session, Exception e) {
                Log.write("Disconnection (" + session.getHttpSession().getId() + ")");
            }

            @Override
            public void onReceived(ServletSession session, Object object) {
                Log.write("Received " + object + " (" + session.getHttpSession().getId() + ")");
            }
        });
    }

    @Override
    protected ServletSession createSession() {
        return new ServletSession(_server);
    }
}
