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

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.Test;

import com.objectfabric.TestsHelper;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOListener;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.SeparateClassLoader;

public class NIOTest extends TestsHelper {

    public static final int PORT = 4444;

    @Test
    public void run1() throws Exception {
        run(1);
    }

    @Test
    public void run8() throws Exception {
        run(8);
    }

    public void run(int count) throws Exception {
        Debug.ProcessName = "Server";

        NIOListener server = new NIOListener() {

            @Override
            public NIOConnection createConnection() {
                Log.write("Connection");
                return new Session();
            }
        };

        server.start(PORT);

        ArrayList<SeparateClassLoader> clients = new ArrayList<SeparateClassLoader>();

        for (int i = 0; i < count; i++) {
            SeparateClassLoader client = new SeparateClassLoader("Client Thread " + i, NIOTestSocketClient.class.getName(), false);

            clients.add(client);
            client.start();
        }

        for (SeparateClassLoader client : clients)
            client.join();

        server.stop();

        Debug.ProcessName = "";
    }

    private static final class Session extends NIOConnection {

        private final Queue<byte[]> _queue = new Queue<byte[]>();

        // private int _read;

        @Override
        protected void onWriteStopped(Exception e) {
            super.onWriteStopped(e);

            Log.write("Disconnection");
        }

        @Override
        protected void read(ByteBuffer buffer) {
            byte[] copy = new byte[buffer.remaining()];
            System.arraycopy(buffer.array(), buffer.position(), copy, 0, buffer.remaining());
            buffer.position(buffer.limit());

            synchronized (_queue) {
                _queue.add(copy);
            }

            requestWrite();
        }

        @Override
        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            synchronized (_queue) {
                byte[] copy = _queue.peek();

                if (copy != null) {
                    int remaining = buffer.remaining();

                    if (remaining >= copy.length) {
                        buffer.put(copy);
                        _queue.poll();
                    } else {
                        buffer.put(copy, 0, remaining);
                        byte[] left = new byte[copy.length - remaining];
                        System.arraycopy(copy, remaining, left, 0, left.length);
                        _queue.set(0, left);
                    }

                    return false;
                }
            }

            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        NIOTest test = new NIOTest();

        for (int i = 0; i < 100; i++)
            test.run(8);
    }
}
