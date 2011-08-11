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

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOListener;
import com.objectfabric.misc.Queue;

public class DistributedSerializationTest extends SerializationTestJava {

    private final ConcurrentLinkedQueue<byte[]> _reads = new ConcurrentLinkedQueue<byte[]>();

    private final ConcurrentLinkedQueue<byte[]> _writes = new ConcurrentLinkedQueue<byte[]>();

    private NIOListener listener = new NIOListener() {

        @Override
        protected NIOConnection createConnection() {
            return new Connection();
        }
    };

    private class Connection extends NIOConnection {

        @Override
        protected void read(ByteBuffer buffer) {
            byte[] copy = new byte[buffer.limit()];
            System.arraycopy(buffer.array(), 0, copy, 0, copy.length);
            _reads.add(copy);
        }

        @Override
        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            byte[] write = _writes.poll();
            System.arraycopy(write, 0, buffer.array(), 0, write.length);
            buffer.position(0);
            buffer.limit(write.length);
            return false;
        }
    }

    @Override
    protected byte[] exchange(byte[] buffer) {
        _writes.add(buffer);
        byte[] read = _reads.poll();
        return read != null ? read : new byte[0];
    }

    public static void main(String[] args) throws Exception {
        DistributedSerializationTest test = new DistributedSerializationTest();

        test.listener.start(4444);

        test.test1();
        test.test2();
        test.test3();
        test.test4();

        TestsHelper.assertIdleAndCleanup();
    }
}