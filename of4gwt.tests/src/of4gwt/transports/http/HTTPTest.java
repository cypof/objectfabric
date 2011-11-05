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

import com.objectfabric.transports.NIOTestHTTP;

public class HTTPTest {

    public static final int WRITES = (int) 1e6;

    public static void run() {
        final DataGen algo = new DataGen(NIOTestHTTP.WRITES, WRITES);
        algo.start();

        final CometTransport transport = new CometTransport() {

            @Override
            protected HTTPRequestBase createRequest(boolean serverToClient) {
                HTTPRequestBase request = HTTPClient.createRequestStatic("http://localhost:4444", serverToClient);
                return request;
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
            protected void onError(Exception e) {
                Log.write(e);
            }
        };

        algo.setConnection(transport);
        transport.connect();
    }
}
