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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import com.objectfabric.AsyncOptions;
import com.objectfabric.CompileTimeSettings;
import com.objectfabric.OF.AutoCommitPolicy;
import com.objectfabric.Privileged;
import com.objectfabric.Strings;
import com.objectfabric.misc.NIOTask.Connect;
import com.objectfabric.misc.NIOTask.Listen;

public final class NIOManager extends Privileged implements Executor, Closeable {

    /**
     * Allows TLS packet decode in one shot, also seems to be default on Linux.
     */
    public static final int SOCKET_BUFFER_SIZE = 32768; // TODO: tune

    interface NIOAttachement {

        void onCancelledKey();
    }

    private static final NIOManager _instance = new NIOManager();

    private final Selector _selector;

    private final ReentrantLock _lock = new ReentrantLock();

    private final Thread[] _threads;

    private final ConcurrentLinkedQueue<Object> _tasks = new ConcurrentLinkedQueue<Object>();

    private final HashMap<SelectionKey, Void> _keys = new HashMap<SelectionKey, Void>();

    private final Queue<SelectionKey> _accepts = new Queue<SelectionKey>();

    private final Queue<SelectionKey> _connects = new Queue<SelectionKey>();

    private final Queue<SelectionKey> _reads = new Queue<SelectionKey>();

    private final Queue<SelectionKey> _writes = new Queue<SelectionKey>();

    private final Queue<SelectionKey> _cancelled = new Queue<SelectionKey>();

    private boolean _shutdown;

    static int _debugPurposesMinBufferLength;

    @SuppressWarnings("unchecked")
    private NIOManager() {
        try {
            _selector = SelectorProvider.provider().openSelector();
        } catch (IOException ex) {
            // Should not occur
            throw new RuntimeException(ex);
        }

        int count = Runtime.getRuntime().availableProcessors();

        if (Debug.ONE_NIO_THREAD)
            count = 1;

        ArrayList<Thread> threads = new ArrayList<Thread>();

        for (int i = 0; i < count; i++) {
            Thread thread = new Thread() {

                @Override
                public void run() {
                    // NIO threads manage transactions
                    forceAutoCommitPolicy(AutoCommitPolicy.DELAYED_MANUAL);

                    if (Debug.ENABLED)
                        setNoTransaction(true);

                    // TODO: direct buffers
                    ByteBuffer readBuffer = ByteBuffer.allocate(SOCKET_BUFFER_SIZE);
                    ByteBuffer writeBuffer = ByteBuffer.allocate(SOCKET_BUFFER_SIZE);

                    while (!_shutdown) {
                        if (Debug.THREADS)
                            ThreadAssert.assertCurrentIsEmpty();

                        NIOManager.this.run(readBuffer, writeBuffer);

                        if (Debug.THREADS)
                            ThreadAssert.assertCurrentIsEmpty();
                    }
                }
            };

            String name = "ObjectFabric NIO " + i;

            if (Debug.ENABLED)
                name = Debug.ProcessName + " " + name;

            thread.setName(name);
            thread.setDaemon(true);

            threads.add(thread);
        }

        for (Thread thread : threads)
            thread.start();

        _threads = threads.toArray(new Thread[threads.size()]);

        if (Debug.ENABLED)
            Debug.assertion(!_lock.isFair());
    }

    public static NIOManager getInstance() {
        return _instance;
    }

    final Selector getSelector() {
        return _selector;
    }

    static void assertLocked() {
        if (Debug.ENABLED)
            Debug.assertion(getInstance()._lock.getHoldCount() > 0);
    }

    static void assertUnlocked() {
        if (Debug.ENABLED)
            Debug.assertion(getInstance()._lock.getHoldCount() == 0);
    }

    /**
     * Testing purposes only! Does not call onStopped methods on NIOConnections.
     */
    @SuppressWarnings("static-access")
    public final void close() {
        _shutdown = true;

        while (!disposed()) {
            wakeup();

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }

        try {
            _selector.close();
        } catch (IOException ex) {
            // Ignore
        }
    }

    private final boolean disposed() {
        for (Thread thread : _threads)
            if (thread.isAlive())
                return false;

        return true;
    }

    // Executor

    public final void execute(Runnable runnable) {
        _tasks.add(runnable);
        _selector.wakeup();
    }

    public final boolean scheduled(Runnable runnable) {
        return _tasks.contains(runnable);
    }

    // NIO

    public final void wakeup() {
        _selector.wakeup();
    }

    final void execute(NIOTask task) {
        if (Debug.ENABLED)
            Debug.assertion(!scheduled(task));

        _tasks.add(task);
        _selector.wakeup();
    }

    public final void connect(NIOConnection connection, InetAddress host, int port) throws IOException {
        Future<Void> future = connect(connection, host, port, null, null);

        try {
            if (CompileTimeSettings.DISALLOW_THREAD_BLOCKING)
                throw new RuntimeException(Strings.THREAD_BLOCKING_DISALLOWED);

            future.get();
        } catch (InterruptedException ex) {
            throw new IOException(ex.getMessage());
        } catch (ExecutionException ex) {
            throw (IOException) ex.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    public final Future<Void> connect(NIOConnection connection, InetAddress host, int port, AsyncCallback<Void> callback, AsyncOptions asyncOptions) {
        try {
            SocketChannel channel = SocketChannel.open();
            // TODO: bench channel.socket().setTcpNoDelay(true);
            channel.socket().setSendBufferSize(SOCKET_BUFFER_SIZE);
            channel.socket().setReceiveBufferSize(SOCKET_BUFFER_SIZE);
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(host, port));

            Connect connect = new Connect(connection, channel, callback, asyncOptions);
            execute(connect);

            return connect.getFuture();
        } catch (IOException ex) {
            FutureAccessor<Void> future = new FutureAccessor<Void>(callback, asyncOptions);
            future.setException(ex);
            return future;
        }
    }

    //

    private final void run(final ByteBuffer readBuffer, final ByteBuffer writeBuffer) {
        _lock.lock();

        for (;;) {
            /*
             * Executor tasks
             */
            for (;;) {
                Object task = _tasks.poll();

                if (task == null)
                    break;

                if (task instanceof NIOTask) {
                    NIOTask nio = (NIOTask) task;
                    Throwable throwable = null;

                    try {
                        Object result = nio.waitOnSelector();

                        if (result != null) {
                            _lock.unlock();

                            try {
                                nio.waitOnSelectorSucceeded(result);
                                return;
                            } catch (Throwable t) {
                                throwable = t;
                            }
                        }
                    } catch (Throwable t) {
                        _lock.unlock();
                        throwable = t;
                    }

                    if (throwable != null) {
                        if (throwable instanceof IOException || throwable instanceof CancelledKeyException)
                            throwable = new ClosedConnectionException(throwable);

                        nio.stop(throwable);
                        return;
                    }
                } else {
                    _lock.unlock();

                    Runnable runnable = (Runnable) task;

                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        Log.write(t.toString());
                    }

                    return;
                }
            }

            /*
             * NIO keys.
             */

            // Accept
            {
                SelectionKey key = _accepts.poll();

                if (key != null) {
                    _lock.unlock();

                    Listen listen = (Listen) key.attachment();

                    if (Debug.ENABLED)
                        Debug.assertion(listen.getKey() == key);

                    listen.select();
                    return;
                }
            }

            // Connect
            {
                SelectionKey key = _connects.poll();

                if (key != null) {
                    _lock.unlock();

                    Connect connect = (Connect) key.attachment();

                    if (Debug.ENABLED)
                        Debug.assertion(connect.getKey() == key);

                    connect.select();
                    return;
                }
            }

            // Read
            {
                SelectionKey key = _reads.poll();

                if (key != null) {
                    NIOConnection connection = (NIOConnection) key.attachment();
                    boolean result = connection.getRead().selecting();

                    _lock.unlock();

                    if (Debug.ENABLED)
                        Debug.assertion(connection.getRead().getKey() == key);

                    connection.getRead().select(readBuffer, result);
                    return;
                }
            }

            // Write
            {
                SelectionKey key = _writes.poll();

                if (key != null) {
                    _lock.unlock();

                    NIOConnection connection = (NIOConnection) key.attachment();

                    if (Debug.ENABLED)
                        Debug.assertion(connection.getWrite().getKey() == key);

                    connection.getWrite().select(writeBuffer);
                    return;
                }
            }

            // Cancel
            {
                SelectionKey key = _cancelled.poll();

                if (key != null) {
                    _keys.remove(key);

                    _lock.unlock();

                    NIOAttachement attachement = (NIOAttachement) key.attachment();
                    attachement.onCancelledKey();
                    return;
                }
            }

            /*
             * Nothing left to do, search for new keys.
             */

            if (_shutdown) {
                _lock.unlock();

                return;
            }

            int size = _selector.keys().size();

            try {
                _selector.select();
            } catch (IOException ex) {
                _lock.unlock();
                throw new RuntimeException(ex);
            }

            /*
             * This is needed because selector's canceled keys set is not accessible.
             * There doesn't seem to be a way to detect keys that are canceled by another
             * thread while current thread is not in select() method. Once select() is
             * called, keys are removed silently by the selector and it is necessary to
             * search for them afterward.
             */
            if (_selector.keys().size() != size)
                for (SelectionKey key : _keys.keySet())
                    if (!_selector.keys().contains(key))
                        _cancelled.add(key);

            //

            Set<SelectionKey> selected = _selector.selectedKeys();

            for (SelectionKey key : selected) {
                try {
                    if (key.isAcceptable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);
                        _accepts.add(key);
                    }

                    if (key.isConnectable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                        _connects.add(key);
                    }

                    if (key.isReadable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                        _reads.add(key);
                    }

                    if (key.isWritable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        _writes.add(key);
                    }
                } catch (CancelledKeyException ex) {
                    _cancelled.add(key);
                }
            }

            selected.clear();
        }
    }

    final void onNewKey(SelectionKey key) {
        if (Debug.ENABLED)
            assertLocked();

        _keys.put(key, null);
    }

    // Debug

    public static void setDebugPurposesMinBufferLength(int value) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        _debugPurposesMinBufferLength = value;
    }
}
