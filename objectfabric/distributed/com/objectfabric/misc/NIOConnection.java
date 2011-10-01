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

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.objectfabric.Strings;
import com.objectfabric.misc.NIOManager.NIOAttachement;

public abstract class NIOConnection extends ConnectionState implements NIOAttachement {

    private NIOTask.Read _read;

    private NIOTask.Write _write;

    private volatile boolean _readDisposed, _writeDisposed;

    public NIOConnection() {
        NIOManager.assertUnlocked();
    }

    public final SocketChannel getChannel() {
        if (_write == null)
            throw new RuntimeException(Strings.NOT_STARTED);

        return (SocketChannel) _write.getChannel();
    }

    @SuppressWarnings("static-access")
    public final void close() {
        /*
         * Volatile read to synchronize with assignment.
         */
        readState();

        if (_write == null)
            throw new RuntimeException(Strings.NOT_STARTED);

        _write.getKey().cancel();
        requestWrite();

        while (!_readDisposed || !_writeDisposed) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
    }

    //

    final NIOTask.Read getRead() {
        return _read;
    }

    final NIOTask.Write getWrite() {
        return _write;
    }

    final void start(NIOTask.Read read, NIOTask.Write write) {
        if (_read != null)
            throw new RuntimeException(Strings.ALREADY_STARTED);

        NIOManager.assertUnlocked();

        if (Debug.ENABLED)
            Debug.assertion(_read == null && _write == null);

        _read = read;
        _write = write;

        /*
         * Volatile write to synchronize with volatile read in askWrite().
         */
        onStarting();

        if (Debug.THREADS) {
            ThreadAssert.assertCurrentIsEmpty();
            ThreadAssert.addPrivate(_read);
        }

        OverrideAssert.add(_read);
        onReadStarted();
        OverrideAssert.end(_read);

        if (Debug.ENABLED)
            ThreadAssert.suspend(read);

        if (Debug.THREADS) {
            ThreadAssert.assertCurrentIsEmpty();
            ThreadAssert.addPrivate(_write);
        }

        OverrideAssert.add(_write);
        onWriteStarted();
        OverrideAssert.end(_write);

        if (Debug.ENABLED)
            ThreadAssert.suspend(write);

        onStarted();

        NIOManager.getInstance().execute(_read);
    }

    final void stop(Exception e) {
        /*
         * Volatile read to synchronize with assignment.
         */
        readState();

        if (_read != null)
            _read.stop(e);

        if (_write != null)
            _write.stop(e);
    }

    public final void onCancelledKey() {
        stop(new ClosedConnectionException());
    }

    //

    protected void onReadStarted() {
        if (Debug.COMMUNICATIONS_LOG)
            Log.write(this + ".onReadStarted()");

        if (Debug.THREADS)
            ThreadAssert.assertPrivate(_read);

        OverrideAssert.set(_read);
    }

    protected void onReadStopped(Exception e) {
        if (Debug.COMMUNICATIONS_LOG)
            Log.write(this + ".onReadStopped()");

        if (Debug.THREADS)
            ThreadAssert.assertPrivate(_read);

        OverrideAssert.set(_read);
    }

    final void onReadDisposed() {
        _readDisposed = true;
    }

    protected void onWriteStarted() {
        if (Debug.COMMUNICATIONS_LOG)
            Log.write(this + ".onWriteStarted()");

        if (Debug.THREADS)
            ThreadAssert.assertPrivate(_write);

        OverrideAssert.set(_write);
    }

    protected void onWriteStopped(Exception e) {
        if (Debug.COMMUNICATIONS_LOG)
            Log.write(this + ".onWriteStopped()");

        if (Debug.THREADS)
            ThreadAssert.assertPrivate(_write);

        OverrideAssert.set(_write);

        if (e != null) {
            if (e instanceof java.lang.SecurityException)
                Log.write("Closing a connection: " + e);

            if (!(e instanceof ClosedConnectionException) || Debug.COMMUNICATIONS_LOG)
                Log.write(e);
        }
    }

    final void onWriteDisposed() {
        _writeDisposed = true;
    }

    //

    @Override
    protected void startWrite() {
        NIOManager.getInstance().execute(_write);
    }

    //

    protected abstract void read(ByteBuffer buffer);

    /**
     * @return true if done, false if more data pending.
     */
    protected abstract boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers);
}