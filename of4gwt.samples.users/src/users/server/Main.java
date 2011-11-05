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

package users.server;

import java.util.concurrent.ConcurrentHashMap;

import com.objectfabric.Connection;
import com.objectfabric.TObject;
import com.objectfabric.Validator;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.socket.SocketServer;

public class Main {
    
    // TODO finish

    public static void main(String[] args) throws Exception {
        // Authorized users

        final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<String, String>();

        users.put("Joe", "pass1");
        users.put("Bob", "pass2");

        // Accept http connections

        SocketServer server = new SocketServer(8080);
        server.addFilter(new HTTP());

        // Listen to connections

        server.setValidator(new Validator() {

            public void validateConnection(Connection connection) {
            }

            public void validateRead(Connection connection, TObject object) {
            }

            public void validateWrite(Connection connection, TObject object) {
            }

            public void validateMethodCall(Connection connection, TObject object, String methodName) {
            }
        });

        server.start();

        PlatformConsole.writeLine("Started http server.");
        PlatformConsole.readLine();
    }
}
