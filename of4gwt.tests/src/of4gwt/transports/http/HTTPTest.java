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

package of4gwt.transports.http;

import of4gwt.misc.Log;
import of4gwt.transports.DataGen;

public class HTTPTest {

    public static final int WRITES = (int) 1e7;

    public static void run() {
        final DataGen algo = new DataGen(WRITES, WRITES);
        // algo.start();

        CometTransport transport = new CometTransport(true) {

            @Override
            protected HTTPRequestBase createRequest(boolean serverToClient) {
                try {
                    return new HTTPClient.HTTPRequest("http://localhost:4444", serverToClient, null);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            protected void read(byte[] buffer, int offset, int limit) {
                algo.read(buffer, offset, limit);

                if (algo.isDone())
                    Log.write("Success!");
            }

            @Override
            protected int write(byte[] buffer, int offset, int limit) {
                int written = algo.write(buffer, offset, limit);

                if (algo.isDone())
                    Log.write("Success!");

                return written;
            }

            @Override
            protected void onError(Throwable t) {
                Log.write(t);
            }
        };

        transport.connect();
    }
}
