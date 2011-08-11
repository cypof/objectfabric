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

import java.util.concurrent.atomic.AtomicInteger;

import com.objectfabric.Privileged;
import com.objectfabric.Strings;

public abstract class ConnectionState extends Privileged {

    private static final int STARTING = 1, STARTING_AND_NOTIFIED = 2, IDLE = 3, NOTIFIED = 4, WRITING = 5, WRITING_AND_NOTIFIED = 6, DISPOSED = 7;

    private final AtomicInteger _state = new AtomicInteger();

    protected final void readState() {
        _state.get();
    }

    protected final void onStarting() {
        if (_state.get() != 0)
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _state.set(STARTING);
    }

    protected final void onStarted() {
        if (!_state.compareAndSet(STARTING, IDLE)) {
            if (Debug.ENABLED)
                Debug.assertion(_state.get() == STARTING_AND_NOTIFIED);

            _state.set(IDLE);
            requestWrite();
        }
    }

    public final void requestWrite() {
        for (;;) {
            int state = _state.get();

            switch (state) {
                case 0:
                    throw new RuntimeException(Strings.NOT_STARTED);
                case STARTING_AND_NOTIFIED:
                case WRITING_AND_NOTIFIED:
                case NOTIFIED:
                case DISPOSED:
                    return;
                case STARTING: {
                    if (_state.compareAndSet(state, STARTING_AND_NOTIFIED))
                        return;

                    break;
                }
                case IDLE: {
                    if (_state.compareAndSet(state, NOTIFIED)) {
                        startWrite();
                        return;
                    }

                    break;
                }
                case WRITING: {
                    if (_state.compareAndSet(state, WRITING_AND_NOTIFIED))
                        return;

                    break;
                }
            }
        }
    }

    protected abstract void startWrite();

    public final boolean onWriteStarting() {
        if (!_state.compareAndSet(NOTIFIED, WRITING)) {
            if (Debug.ENABLED)
                Debug.assertion(_state.get() == DISPOSED);

            return false;
        }

        return true;
    }

    public final void endWrite() {
        for (;;) {
            if (_state.get() == WRITING) {
                if (_state.compareAndSet(WRITING, IDLE))
                    break;
            } else {
                if (Debug.ENABLED)
                    Debug.assertion(_state.get() == WRITING_AND_NOTIFIED);

                if (_state.compareAndSet(WRITING_AND_NOTIFIED, NOTIFIED)) {
                    startWrite();
                    break;
                }
            }
        }
    }

    public final boolean isDisposed() {
        return _state.get() == DISPOSED;
    }

    public final boolean dispose() {
        for (;;) {
            int state = _state.get();

            switch (state) {
                case 0:
                    throw new RuntimeException(Strings.NOT_STARTED);
                case STARTING: {
                    if (_state.compareAndSet(STARTING, DISPOSED))
                        return true;

                    break;
                }
                case STARTING_AND_NOTIFIED: {
                    if (_state.compareAndSet(STARTING_AND_NOTIFIED, DISPOSED))
                        return true;

                    break;
                }
                case NOTIFIED: {
                    if (_state.compareAndSet(NOTIFIED, DISPOSED))
                        return true;

                    break;
                }
                case IDLE: {
                    if (_state.compareAndSet(IDLE, DISPOSED))
                        return true;

                    break;
                }
                case WRITING:
                case WRITING_AND_NOTIFIED:
                    return false;
                case DISPOSED:
                    return false;
            }
        }
    }

    public final void setIdle() {
        if (Debug.ENABLED)
            assertWriting();

        _state.set(IDLE);
    }

    public final void setNotified() {
        if (Debug.ENABLED)
            assertWriting();

        _state.set(NOTIFIED);
    }

    // Debug

    public final void assertStarting() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == STARTING);
    }

    public final void assertNotified() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == NOTIFIED || state == DISPOSED);
    }

    public final void assertWriting() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == WRITING || state == WRITING_AND_NOTIFIED);
    }

}