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

package com.objectfabric.transports;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Queue;

public class NIOTestSocketClient {

    private static Connection _connection;

    public static void main() throws IOException, InterruptedException {
        Debug.ProcessName = "Client";

        NIOManager manager = NIOManager.getInstance();
        InetAddress host = InetAddress.getLocalHost();
        _connection = new Connection();
        manager.connect(_connection, host, 4444);

        while (!_connection.isDone())
            Thread.sleep(1);

        _connection.close();

        Thread.sleep(100);

        NIOManager.getInstance().close();
        PlatformAdapter.shutdown();
    }

    private static final class Connection extends NIOConnection {

        private DataGen _test = new DataGen(10000, 10000);

        public Connection() {
            _test.start();
        }

        public boolean isDone() {
            return _test.isDone();
        }

        @Override
        protected void onWriteStarted() {
            super.onWriteStarted();

            requestWrite();
        }

        @Override
        protected void read(ByteBuffer buffer) {
            _test.read(buffer.array(), buffer.position(), buffer.limit());
            buffer.position(buffer.limit());
        }

        @Override
        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            int length = _test.write(buffer.array(), buffer.position(), buffer.limit());
            buffer.position(buffer.position() + (length >= 0 ? length : -length - 1));
            return length < 0;
        }
    }
}
