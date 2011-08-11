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

package com.objectfabric.transports.bench;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import junit.framework.Assert;

import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.Queue;

class Data {

    protected final CopyOnWriteArrayList<TestConnection> _connections = new CopyOnWriteArrayList<TestConnection>();

    protected final Object _lock = new Object();

    protected boolean _sending;

    public final AtomicLong _latencySum = new AtomicLong();

    public final AtomicLong _latencyCount = new AtomicLong();

    public Data() {
    }

    protected void log(String text) {
    }

    protected void sendData() {
        synchronized (_lock) {
            if (!_sending) {
                _sending = true;

                for (final TestConnection connection : _connections) {
                    connection.startData();
                }
            }
        }
    }

    protected void stopData() {
        synchronized (_lock) {
            if (_sending) {
                _sending = false;

                for (final TestConnection connection : _connections) {
                    connection.stopData();
                }
            }
        }
    }

    /**
     * This class is created by servers and clients to generate data.
     */
    protected class TestConnection extends NIOConnection {

        private volatile boolean _writing;

        public volatile int _writes;

        public volatile int _reads;

        public int _lastWrites, _lastReads;

        private int _writtenNumber, _readNumber;

        @Override
        protected void onWriteStarted() {
            log("Connection from " + getChannel().socket().getRemoteSocketAddress());

            synchronized (_lock) {
                _connections.add(this);

                if (_sending) {
                    startData();
                }
            }

            super.onWriteStarted();
        }

        @Override
        protected void onWriteStopped(Throwable t) {
            super.onWriteStopped(t);

            log("Disconnected from " + getChannel().socket().getRemoteSocketAddress());

            synchronized (_lock) {
                _connections.remove(this);

                if (_sending) {
                    stopData();
                }
            }
        }

        @Override
        protected void read(ByteBuffer buffer) {
            _reads++;

            int offset = buffer.position();

            while (offset + 4 <= buffer.limit()) {
                int b0 = buffer.array()[offset++] & 0x000000ff;
                int b1 = (buffer.array()[offset++] << 8) & 0x0000ff00;
                int b2 = (buffer.array()[offset++] << 16) & 0x00ff0000;
                int b3 = (buffer.array()[offset++] << 24) & 0xff000000;
                int number = b3 | b2 | b1 | b0;
                Assert.assertEquals(_readNumber++, number);
            }

            buffer.position(buffer.limit());
        }

        @Override
        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            if (!_writing)
                return false;

            _writes++;

            int offset = buffer.position();

            while (offset + 4 <= buffer.limit()) {
                buffer.array()[offset++] = (byte) (_writtenNumber & 0xff);
                buffer.array()[offset++] = (byte) ((_writtenNumber >>> 8) & 0xff);
                buffer.array()[offset++] = (byte) ((_writtenNumber >>> 16) & 0xff);
                buffer.array()[offset++] = (byte) ((_writtenNumber >>> 24) & 0xff);
                _writtenNumber++;
            }

            buffer.position(offset);
            return false;
        }

        // Data generation

        public void startData() {
            _writing = true;
            requestWrite();
        }

        public void stopData() {
            _writing = false;
        }
    }
}
