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

package com.objectfabric.transports.socket;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.objectfabric.Connection;
import com.objectfabric.Site;
import com.objectfabric.Transaction;
import com.objectfabric.Validator;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.Queue;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;

public abstract class SocketConnection extends Connection {

    public static final byte[] OF_MAGIC_NUMBER = new byte[] { 96, 70, 104, -110, 122, 118, 67, -56 };

    private final LastFilter _lastFilter = new LastFilter();

    private SocketAddress _remote;

    protected SocketConnection(Transaction trunk, Site target, Validator validator) {
        super(trunk, target, validator);
    }

    @Override
    protected void close_(Exception e) {
        super.close_(e);

        _lastFilter.close();
    }

    //

    public SocketChannel getSocketChannel() {
        Filter filter = _lastFilter;

        for (;;) {
            if (filter instanceof PhysicalConnection)
                return ((PhysicalConnection) filter).getChannel();

            filter = filter.getPrevious();

            if (filter == null) {
                // Possible if http and waiting for client reconnection
                return null;
            }
        }
    }

    /**
     * Saves the address so that it can be logged on disconnection, otherwise it is too
     * late to retrieve it. Socket.getRemoteAddress() returns null once disconnected.
     */
    public SocketAddress getRemoteAddress() {
        return _remote;
    }

    @Override
    protected void onWriteStarted() {
        super.onWriteStarted();

        _remote = getSocketChannel().socket().getRemoteSocketAddress();
    }

    final LastFilter getLastFilter() {
        return _lastFilter;
    }

    //

    @Override
    protected void requestWrite() {
        _lastFilter.requestWrite();
    }

    //

    /**
     * This first filter is the physical connection, which raises read & write events etc.
     */
    static final class PhysicalConnection extends NIOConnection implements Filter {

        private Filter _next;

        public void init(List<FilterFactory> factories, int index, boolean clientSide) {
            if (Debug.ENABLED)
                Debug.assertion(index == 0);

            _next = factories.get(1).createFilter(clientSide);
            _next.init(factories, 1, clientSide);
            _next.setPrevious(this);
        }

        public Filter getPrevious() {
            return null;
        }

        public void setPrevious(Filter value) {
            throw new UnsupportedOperationException();
        }

        public Filter getNext() {
            return _next;
        }

        public void setNext(Filter value) {
            _next = value;
        }

        public Connection getConnection() {
            throw new UnsupportedOperationException();
        }

        //

        @Override
        public void requestWrite() {
            requestRun();
        }

        //

        @Override
        public void onReadStarted() {
            super.onReadStarted();
            _next.onReadStarted();
        }

        @Override
        public void onReadStopped(Exception e) {
            super.onReadStopped(e);
            _next.onReadStopped(e);
        }

        @Override
        public void onWriteStarted() {
            super.onWriteStarted();
            _next.onWriteStarted();
        }

        @Override
        public void onWriteStopped(Exception e) {
            super.onWriteStopped(e);
            _next.onWriteStopped(e);
        }

        @Override
        public void read(ByteBuffer buffer) {
            _next.read(buffer);
        }

        @Override
        public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            return _next.write(buffer, headers);
        }
    }

    /**
     * The last filter simply redirects events to the logical connection for processing.
     */
    final class LastFilter implements Filter {

        private Filter _previous;

        public void init(List<FilterFactory> factories, int index, boolean clientSide) {
            // End of chain, do nothing
        }

        public Filter getPrevious() {
            return _previous;
        }

        public void setPrevious(Filter value) {
            _previous = value;
        }

        public Filter getNext() {
            return null;
        }

        public void setNext(Filter value) {
            throw new UnsupportedOperationException();
        }

        public Connection getConnection() {
            return SocketConnection.this;
        }

        //

        public void close() {
            _previous.close();
        }

        public void requestWrite() {
            _previous.requestWrite();
        }

        //

        public void onReadStarted() {
            SocketConnection.this.startRead();
        }

        public void onReadStopped(Exception e) {
            SocketConnection.this.stopRead(e);
        }

        public void onWriteStarted() {
            SocketConnection.this.startWrite();
        }

        public void onWriteStopped(Exception e) {
            SocketConnection.this.stopWrite(e);
        }

        public void read(ByteBuffer buffer) {
            SocketConnection.this.read(buffer.array(), buffer.position(), buffer.limit());
            buffer.position(buffer.limit());
        }

        public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            int length = SocketConnection.this.write(buffer.array(), buffer.position(), buffer.limit());
            buffer.position(buffer.position() + (length >= 0 ? length : -length - 1));
            return length < 0;
        }
    }
}
