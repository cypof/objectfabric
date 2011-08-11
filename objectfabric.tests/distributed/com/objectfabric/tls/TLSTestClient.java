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

package com.objectfabric.tls;

import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.Privileged;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.filters.TLS;
import com.objectfabric.transports.socket.SocketClient;

public class TLSTestClient extends Privileged {

    public static void main(AtomicInteger progress, int number) throws Exception {
        Debug.ProcessName = "Client " + number;

        SimpleObjectModel.register();

        SocketClient client = new SocketClient("localhost", 4444);
        client.addFilter(new TLS(TLSTest.createSSLContext()));
        client.connect();
        client.send(new SimpleClass());
        SeparateClassLoader.waitForInterruption(client, NIOManager.getInstance());
    }
}
