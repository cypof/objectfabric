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

import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.Connection;
import com.objectfabric.Site;
import com.objectfabric.Transaction;
import com.objectfabric.Validator;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.misc.ThreadAssert;

public abstract class VMConnection extends Connection {

    public static final int EXIT = Integer.MAX_VALUE;

    private final RandomSplitter _reader = new RandomSplitter();

    enum Status {
        IDLE, NOTIFIED, RUNNING
    }

    private final AtomicReference<Status> _status = new AtomicReference<Status>(Status.IDLE);

    private byte[] _buffer = new byte[5000];

    private int _length = 0;

    private SeparateClassLoader _classLoader;

    protected VMConnection(Transaction trunk, Site target, Validator validator) {
        super(trunk, target, validator);

        if (Debug.ENABLED)
            setNoTransaction(true);

        startRead();
        startWrite();

        if (Debug.ENABLED)
            setNoTransaction(false);
    }

    public final byte[] getBuffer() {
        return _buffer;
    }

    public int length() {
        return _length;
    }

    public void setLength(int value) {
        _length = value;
    }

    @Override
    protected void close_(Throwable throwable) {
        super.close_(throwable);

        if (Debug.ENABLED)
            setNoTransaction(true);

        stopRead(throwable);
        stopWrite(throwable);

        if (Debug.ENABLED)
            setNoTransaction(false);
    }

    public int transfer(byte[] buffer, int length) {
        if (Debug.ENABLED) {
            ThreadAssert.suspend(Site.getLocal());
            ThreadAssert.resume(this, false);
            assertCurrentTransactionNull();
            setNoTransaction(true);
        }

        byte[] temp = _reader.read(buffer, 0, length, 0);
        read(temp, 0, temp.length);
        int written = 0;
        Status status = _status.get();

        if (status == Status.NOTIFIED || status == Status.RUNNING) {
            if (status == Status.NOTIFIED)
                _status.set(Status.RUNNING);

            if (Debug.RANDOMIZE_TRANSFER_LENGTHS)
                written = write(buffer, 0, com.objectfabric.misc.PlatformAdapter.getRandomInt(Debug.RANDOMIZED_TRANSFER_LIMIT));
            else
                written = write(buffer, 0, buffer.length);

            if (written < 0) {
                _status.compareAndSet(Status.RUNNING, Status.IDLE);
                written = -written - 1;
            }
        }

        if (Debug.ENABLED) {
            ThreadAssert.suspend(this);
            ThreadAssert.resume(Site.getLocal());
            assertCurrentTransactionNull();
            setNoTransaction(false);
        }

        return written;
    }

    @Override
    protected final void requestWrite() {
        _status.set(Status.NOTIFIED);
    }

    public SeparateClassLoader getClassLoader() {
        return _classLoader;
    }

    public void setClassLoader(SeparateClassLoader value) {
        _classLoader = value;
    }
}
