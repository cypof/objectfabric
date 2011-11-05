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

package com.objectfabric;

import java.io.IOException;

import org.junit.Ignore;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.socket.SocketClient;

@Ignore
@SuppressWarnings("unchecked")
public class TListDistributedClient extends TListExtended {

    private final TList _pair;

    public TListDistributedClient(TList pair) {
        _pair = pair;
    }

    @Override
    protected ExtendedTest getTest(int threads, int flags) {
        return new Tests(threads, flags, (TList) _pair.get(0), (TList) _pair.get(1));
    }

    public static void main(int number) throws IOException {
        Debug.ProcessName = "Client " + number;
        ExpectedExceptionThrower.disableCounter();

        SocketClient client = new SocketClient("localhost", 4444);
        TList pair = (TList) client.connectAndWaitObject();
        Transaction.setDefaultTrunk(pair.getTrunk());

        TListDistributedClient instance = new TListDistributedClient(pair);

        for (int i = 0; i < ALL; i++)
            instance.test(instance.getTest(4, i), 10, false);

        client.close();
        ExpectedExceptionThrower.enableCounter();
        PlatformAdapter.shutdown();
    }
}
