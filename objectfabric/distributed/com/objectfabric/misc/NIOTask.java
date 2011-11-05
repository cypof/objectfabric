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

package com.objectfabric.misc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;

import com.objectfabric.AsyncOptions;
import com.objectfabric.Privileged;
import com.objectfabric.Reader;
import com.objectfabric.Stats;
import com.objectfabric.misc.NIOManager.NIOAttachement;

abstract class NIOTask extends Privileged implements NIOAttachement, Runnable {

    public abstract Channel getChannel();

    public abstract SelectionKey getKey();

    public abstract NIOConnection getConnection();

    private boolean _stopped;

    public final void stop(Exception e) {
        NIOManager.assertUnlocked();

        OverrideAssert.add(this);
        stop_(e);
        OverrideAssert.end(this);
    }

    public void stop_(Exception e) {
        OverrideAssert.set(this);

        SelectionKey key = getKey();

        if (key != null)
            key.cancel();

        Channel channel = getChannel();

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ex) {
                // Ignore
            }
        }

        if (!_stopped) {
            _stopped = true;

            NIOConnection connection = getConnection();

            if (connection != null)
                connection.stop(e);
        }
    }

    public abstract Object waitOnSelector() throws IOException;

    /**
     * @param result
     */
    public void waitOnSelectorSucceeded(Object result) {
        throw new UnsupportedOperationException();
    }

    public final void onCancelledKey() {
        stop(new ClosedConnectionException());
    }

    // For Runnable
    public final void run() {
        throw new IllegalStateException();
    }

    public static final class Listen extends NIOTask {

        private final NIOListener _listener;

        private final ServerSocketChannel _channel;

        private SelectionKey _key;

        public Listen(NIOListener listener, ServerSocketChannel channel) {
            _listener = listener;
            _channel = channel;
        }

        @Override
        public Channel getChannel() {
            return _channel;
        }

        @Override
        public SelectionKey getKey() {
            return _key;
        }

        @Override
        public NIOConnection getConnection() {
            return null;
        }

        @Override
        public Object waitOnSelector() throws IOException {
            NIOManager.assertLocked();
            Selector selector = NIOManager.getInstance().getSelector();
            SelectionKey key = _channel.register(selector, SelectionKey.OP_ACCEPT, this);
            NIOManager.getInstance().onNewKey(key);

            if (Debug.ENABLED)
                Debug.assertion(_key == null || _key == key);

            _key = key;
            selector.wakeup();
            return null;
        }

        public void select() {
            NIOManager.assertUnlocked();

            try {
                ServerSocket serverSocket = _channel.socket();
                Socket socket = serverSocket.accept();
                socket.setSendBufferSize(NIOManager.SOCKET_BUFFER_SIZE);
                socket.setReceiveBufferSize(NIOManager.SOCKET_BUFFER_SIZE);
                // TODO: bench socket.setTcpNoDelay(true);
                SocketChannel channel = socket.getChannel();
                channel.configureBlocking(false);
                NIOConnection connection = _listener.createConnection();
                Register register = new Register(connection, channel, null, false);
                NIOManager.getInstance().execute(register);
            } catch (IOException ex) {
                Log.write(ex);
            }

            NIOManager.getInstance().execute(this);
        }
    }

    public static final class Connect extends NIOTask {

        private final NIOConnection _connection;

        private final String _host;

        private final int _port;

        private final FutureAccessor<Void> _future;

        private SocketChannel _channel;

        private SelectionKey _key;

        public Connect(NIOConnection connection, String host, int port, AsyncCallback<Void> callback, AsyncOptions asyncOptions) {
            _connection = connection;
            _host = host;
            _port = port;

            _future = new FutureAccessor<Void>(callback, asyncOptions) {

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    boolean value = super.cancel(mayInterruptIfRunning);
                    stop(null);
                    return value;
                }
            };
        }

        @Override
        public Channel getChannel() {
            return _channel;
        }

        @Override
        public SelectionKey getKey() {
            return _key;
        }

        @Override
        public NIOConnection getConnection() {
            return _connection;
        }

        public Future<Void> getFuture() {
            return _future;
        }

        @Override
        public Object waitOnSelector() throws IOException {
            NIOManager.assertLocked();
            _channel = SocketChannel.open();
            // TODO: bench _channel.socket().setTcpNoDelay(true);
            _channel.socket().setSendBufferSize(NIOManager.SOCKET_BUFFER_SIZE);
            _channel.socket().setReceiveBufferSize(NIOManager.SOCKET_BUFFER_SIZE);
            _channel.configureBlocking(false);
            _channel.connect(new InetSocketAddress(_host, _port));

            Selector selector = NIOManager.getInstance().getSelector();
            _key = _channel.register(selector, SelectionKey.OP_CONNECT, this);
            NIOManager.getInstance().onNewKey(_key);
            selector.wakeup();
            return null;
        }

        @Override
        public void stop_(Exception e) {
            super.stop_(e);

            // Test necessary to avoid double callbacks
            if (!_future.isDone()) {
                // In case listener was still starting
                _future.setException(e);
            }
        }

        public void select() {
            NIOManager.assertUnlocked();

            ClosedConnectionException ex = null;

            try {
                SocketChannel channel = (SocketChannel) _key.channel();

                if (channel.isConnectionPending()) {
                    channel.finishConnect();
                }
            } catch (IOException e) {
                ex = new ClosedConnectionException(e);
            }

            if (ex != null)
                stop(ex);
            else {
                Register register = new Register(_connection, _channel, _future, true);
                NIOManager.getInstance().execute(register);
            }
        }
    }

    public static final class Register extends NIOTask {

        private final NIOConnection _connection;

        private final SocketChannel _channel;

        private final FutureAccessor<Void> _future;

        private final boolean _client;

        public Register(NIOConnection connection, SocketChannel channel, FutureAccessor<Void> future, boolean client) {
            _connection = connection;
            _channel = channel;
            _future = future;
            _client = client;
        }

        @Override
        public Channel getChannel() {
            return _channel;
        }

        @Override
        public SelectionKey getKey() {
            return null;
        }

        @Override
        public NIOConnection getConnection() {
            return _connection;
        }

        @Override
        public Object waitOnSelector() throws IOException {
            NIOManager.assertLocked();
            Selector selector = NIOManager.getInstance().getSelector();
            // Passing OP_CONNECT is only to avoid bug in Android (2.1)
            SelectionKey key = _channel.register(selector, _client ? SelectionKey.OP_CONNECT : 0, _connection);
            key.interestOps(0);
            NIOManager.getInstance().onNewKey(key);
            return key;
        }

        @Override
        public void waitOnSelectorSucceeded(Object result) {
            NIOManager.assertUnlocked();
            SelectionKey key = (SelectionKey) result;
            Read read = new Read(_connection, _channel, key);
            Write write = new Write(_connection, _channel, key);
            _connection.start(read, write);

            if (_future != null)
                _future.set(null);
        }

        @Override
        public void stop_(Exception e) {
            super.stop_(e);

            // Test necessary to avoid double callbacks
            if (_future != null && !_future.isDone())
                _future.setException(e);
        }
    }

    public static final class Read extends NIOTask {

        private final NIOConnection _connection;

        private final SocketChannel _channel;

        private final SelectionKey _key;

        private static final int WAITING = 0, RUNNING = 1, DISPOSED = 2;

        private int _state = RUNNING;

        public Read(NIOConnection connection, SocketChannel channel, SelectionKey key) {
            _connection = connection;
            _channel = channel;
            _key = key;
        }

        @Override
        public Channel getChannel() {
            return _channel;
        }

        @Override
        public SelectionKey getKey() {
            return _key;
        }

        @Override
        public NIOConnection getConnection() {
            return _connection;
        }

        @Override
        public Object waitOnSelector() throws IOException {
            if (Debug.ENABLED) {
                NIOManager.assertLocked();
                Debug.assertion(_state == RUNNING);
            }

            _state = WAITING;

            try {
                if (Debug.ENABLED)
                    Debug.assertion((_key.interestOps() & SelectionKey.OP_READ) == 0);

                _key.interestOps(_key.interestOps() | SelectionKey.OP_READ);
            } catch (CancelledKeyException ex) {
                return ex;
            }

            return null;
        }

        @Override
        public void waitOnSelectorSucceeded(Object result) {
            if (result != null)
                stop(new ClosedConnectionException((CancelledKeyException) result));
        }

        @Override
        public void stop_(Exception e) {
            super.stop_(e);

            NIOManager.getInstance().execute(new ReadEnd(this, e));
        }

        public boolean selecting() {
            if (_state == WAITING) {
                _state = RUNNING;
                return true;
            }

            if (Debug.ENABLED)
                Debug.assertion(_state == DISPOSED);

            return false;
        }

        public void select(ByteBuffer buffer, boolean lockedResult) {
            NIOManager.assertUnlocked();

            if (lockedResult) {
                if (Debug.ENABLED)
                    ThreadAssert.resume(this);

                if (Debug.THREADS)
                    ThreadAssert.assertPrivate(this);

                for (;;) {
                    buffer.limit(buffer.capacity());
                    buffer.position(Reader.LARGEST_UNSPLITABLE);

                    int length;
                    ClosedConnectionException ex = null;

                    try {
                        length = _channel.read(buffer);
                    } catch (IOException e) {
                        ex = new ClosedConnectionException(e);
                        length = 0;
                    }

                    if (ex != null) {
                        if (Debug.ENABLED)
                            ThreadAssert.suspend(this);

                        stop(ex);
                        NIOManager.getInstance().execute(this);
                        return;
                    }

                    if (length == -1) { // Seems to mean socket closed
                        if (Debug.ENABLED)
                            ThreadAssert.suspend(this);

                        stop(new ClosedConnectionException());
                        NIOManager.getInstance().execute(this);
                        return;
                    }

                    if (length == 0) {
                        if (Debug.ENABLED)
                            ThreadAssert.suspend(this);

                        // Re-enable read for this channel
                        NIOManager.getInstance().execute(this);
                        return;
                    }

                    if (!read(buffer, length))
                        return;
                }
            }
        }

        private final boolean read(ByteBuffer buffer, int length) {
            try {
                buffer.limit(Reader.LARGEST_UNSPLITABLE + length);
                buffer.position(Reader.LARGEST_UNSPLITABLE);

                /*
                 * Should only raise security exceptions (E.g. Validator or SSLEngine)
                 */
                _connection.read(buffer);

                if (Debug.ENABLED)
                    Debug.assertion(buffer.position() == buffer.limit());
            } catch (Exception e) {
                if (Debug.ENABLED) {
                    ThreadAssert.suspend(this);
                    setNoTransaction(true);
                    assertThreadContextEmpty();
                }

                stop(e);
                NIOManager.getInstance().execute(this);
                return false;
            }

            return true;
        }
    }

    public static final class ReadEnd extends NIOTask {

        private final Read _read;

        private final Exception _exception;

        public ReadEnd(Read read, Exception e) {
            _read = read;
            _exception = e;
        }

        @Override
        public Channel getChannel() {
            return _read.getChannel();
        }

        @Override
        public SelectionKey getKey() {
            return _read.getKey();
        }

        @Override
        public NIOConnection getConnection() {
            return _read.getConnection();
        }

        @Override
        public Object waitOnSelector() throws IOException {
            if (_read._state == Read.WAITING) {
                _read._state = Read.DISPOSED;
                return this;
            }

            return null;
        }

        @Override
        public void waitOnSelectorSucceeded(Object result) {
            if (result != null) {
                if (Debug.ENABLED)
                    ThreadAssert.resume(_read);

                if (Debug.THREADS)
                    ThreadAssert.assertPrivate(_read);

                OverrideAssert.add(_read);
                getConnection().onReadStopped(_exception);
                OverrideAssert.end(_read);

                getConnection().onReadDisposed();

                if (Debug.THREADS) {
                    ThreadAssert.removePrivate(_read);
                    ThreadAssert.assertCurrentIsEmpty();
                }
            }
        }
    }

    public static final class Write extends NIOTask {

        private final NIOConnection _connection;

        private final SocketChannel _channel;

        private final SelectionKey _key;

        private final Queue<ByteBuffer> _headers = new Queue<ByteBuffer>();

        //

        /*
         * TODO Stack to keep hot ones on top. Used only if buffer cannot be completely
         * written to socket, should be almost never the case as buffer is calibrated on
         * socket.
         */
        // private static final ConcurrentStack<ByteBuffer> _remainingCache = new
        // ConcurrentStack<ByteBuffer>();
        //
        // private ByteBuffer _remaining;

        public Write(NIOConnection connection, SocketChannel channel, SelectionKey key) {
            _connection = connection;
            _channel = channel;
            _key = key;
        }

        @Override
        public Channel getChannel() {
            return _channel;
        }

        @Override
        public SelectionKey getKey() {
            return _key;
        }

        @Override
        public NIOConnection getConnection() {
            return _connection;
        }

        @Override
        public Object waitOnSelector() throws IOException {
            if (Debug.ENABLED) {
                getConnection().assertScheduledAccessor();
                Debug.assertion((_key.interestOps() & SelectionKey.OP_WRITE) == 0);
            }

            _key.interestOps(_key.interestOps() | SelectionKey.OP_WRITE);
            return null;
        }

        @Override
        public void stop_(Exception e) {
            super.stop_(e);

            NIOManager.getInstance().execute(new WriteEnd(this, e));
        }

        public void select(final ByteBuffer buffer) {
            NIOManager.assertUnlocked();

            if (!_connection.onRunStartingAccessor())
                return;

            if (Debug.ENABLED)
                ThreadAssert.resume(this);

            if (Debug.THREADS)
                ThreadAssert.assertPrivate(this);

            int headers = 0;

            for (int i = 0; i < _headers.size(); i++)
                headers += _headers.get(i).remaining();

            buffer.limit(buffer.capacity() - headers);
            buffer.position(0);
            boolean done;

            try {
                done = _connection.write(buffer, _headers);

                if (Debug.ENABLED)
                    for (int i = 0; i < _headers.size(); i++)
                        Debug.assertion(_headers.get(i).remaining() != 0);
            } catch (Exception e) {
                if (Debug.ENABLED) {
                    ThreadAssert.suspend(this);
                    setNoTransaction(true);
                    assertThreadContextEmpty();
                }

                _connection.setIdleAccessor();
                stop(e);
                return;
            }

            buffer.flip();

            while (_headers.size() > 0 || buffer.remaining() != 0) {
                int written;
                ClosedConnectionException ex = null;

                try {
                    if (_headers.size() == 0)
                        written = _channel.write(buffer);
                    else if (_headers.size() == 1 && buffer.remaining() == 0)
                        written = _channel.write(_headers.get(0));
                    else {
                        ByteBuffer[] array = new ByteBuffer[_headers.size() + 1];

                        for (int i = 0; i < array.length - 1; i++)
                            array[i] = _headers.get(i);

                        array[array.length - 1] = buffer;
                        written = (int) _channel.write(array);
                    }
                } catch (IOException e) {
                    ex = new ClosedConnectionException(e);
                    written = 0;
                }

                if (ex != null) {
                    if (Debug.ENABLED)
                        ThreadAssert.suspend(this);

                    _connection.setIdleAccessor();
                    stop(ex);
                    return;
                }

                if (written == 0) {
                    if (Debug.ENABLED)
                        ThreadAssert.suspend(this);

                    if (buffer.remaining() > 0) { // Maybe we are writing headers
                        // _remaining = _remainingCache.pop();
                        //
                        // if (_remaining == null)
                        // _remaining = ByteBuffer.allocateDirect(buffer.capacity());
                        //
                        // _remaining.clear();
                        // _remaining.put(buffer);

                        ByteBuffer remaining = ByteBuffer.allocate(buffer.remaining());
                        remaining.put(buffer);
                        _headers.add(remaining);

                        if (Stats.ENABLED)
                            Stats.getInstance().SocketRemaining.addAndGet(buffer.remaining());
                    }

                    _connection.setScheduledAccessor();
                    NIOManager.getInstance().execute(this);
                    return;
                }

                for (;;) {
                    ByteBuffer header = _headers.peek();

                    if (header == null || header.remaining() != 0)
                        break;

                    _headers.poll();
                }

                if (Stats.ENABLED)
                    Stats.getInstance().SocketWritten.addAndGet(written);

                // if (_remaining != null) {
                // _remainingCache.push(_remaining);
                // _remaining = null;
                // }
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            if (done)
                _connection.onRunEndedAccessor();
            else {
                _connection.setScheduledAccessor();
                NIOManager.getInstance().execute(this);
            }
        }
    }

    public static final class WriteEnd extends NIOTask {

        private final Write _write;

        private final Exception _exception;

        public WriteEnd(Write write, Exception exception) {
            _write = write;
            _exception = exception;
        }

        @Override
        public Channel getChannel() {
            return _write.getChannel();
        }

        @Override
        public SelectionKey getKey() {
            return _write.getKey();
        }

        @Override
        public NIOConnection getConnection() {
            return _write.getConnection();
        }

        @Override
        public Object waitOnSelector() throws IOException {
            return this;
        }

        @Override
        public void waitOnSelectorSucceeded(Object result) {
            if (!getConnection().isDisposedAccessor()) {
                if (!getConnection().disposeFromOtherThreadAccessor())
                    NIOManager.getInstance().execute(this);
                else {
                    if (Debug.ENABLED)
                        ThreadAssert.resume(_write);

                    if (Debug.THREADS)
                        ThreadAssert.assertPrivate(_write);

                    OverrideAssert.add(_write);
                    getConnection().onWriteStopped(_exception);
                    OverrideAssert.end(_write);

                    getConnection().onWriteDisposed();

                    if (Debug.THREADS) {
                        ThreadAssert.removePrivate(_write);
                        ThreadAssert.assertCurrentIsEmpty();
                    }
                }
            }
        }
    }
}
