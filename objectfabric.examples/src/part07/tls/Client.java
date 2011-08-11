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

package part07.tls;

import javax.net.ssl.SSLContext;

import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.filters.TLS;
import com.objectfabric.transports.socket.SocketClient;

public class Client {

    public static void main(String[] args) throws Exception {
        SocketClient client = new SocketClient("localhost", 8443);
        SSLContext ssl = Shared.createSSLContext();
        client.addFilter(new TLS(ssl));
        client.connect();
        client.send("Done!");

        if (SeparateClassLoader.isTest()) // Testing purposes
            SeparateClassLoader.waitForInterruption(client);
        else {
            System.out.println("Connected over a secure socket, press enter to exit.");
            PlatformConsole.readLine();
        }
    }
}
