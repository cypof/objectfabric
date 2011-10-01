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

package com.objectfabric.transports.http;

import java.net.URL;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.transports.DataGen;
import com.objectfabric.transports.NIOTest;
import com.objectfabric.transports.NIOTestHTTP;

public class NIOTestHTTPClient {

    public static final int WRITES = (int) 1e6;

    public static void main(String[] args) {
        Debug.ProcessName = "Client";

        final DataGen algo = new DataGen(NIOTestHTTP.WRITES, WRITES);
        algo.start();

        CometTransport transport = new CometTransport(false) {

            @Override
            protected HTTPRequestBase createRequest(boolean serverToClient) {
                try {
                    return new HTTPClient.HTTPRequest(new URL("http://localhost:" + NIOTest.PORT), serverToClient);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            protected void read(byte[] buffer, int offset, int limit) {
                algo.read(buffer, offset, limit);
            }

            @Override
            protected int write(byte[] buffer, int offset, int limit) {
                return algo.write(buffer, offset, limit);
            }

            @Override
            protected void onError(Exception e) {
                Log.write(e);
            }
        };

        transport.connect();

        while (!algo.isDone())
            PlatformThread.sleep(1);

        // Let writes propagate
        PlatformThread.sleep(100);

        Log.write("Success!!");
        transport.close();

        PlatformAdapter.shutdown();
    }
}
