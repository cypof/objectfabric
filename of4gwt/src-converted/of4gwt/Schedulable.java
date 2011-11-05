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

package of4gwt;

import of4gwt.misc.AtomicInteger;

import of4gwt.misc.Debug;

/**
 * States and transitions for extensions and connections. They run on a thread pool and
 * can be scheduled to run if not already by calling requestRun().
 */
public abstract class Schedulable extends Privileged {

    private static final int STARTING = 1;

    private static final int STARTING_SCHEDULED = 2;

    private static final int IDLE = 3;

    private static final int SCHEDULED = 4;

    private static final int RUNNING = 5;

    private static final int RUNNING_SCHEDULED = 6;

    private static final int DISPOSED = 7;

    // TODO bench AtomicIntegerFieldUpdater
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
                Debug.assertion(_state.get() == STARTING_SCHEDULED);

            _state.set(IDLE);
            requestRun();
        }
    }

    protected boolean requestRun() {
        for (;;) {
            int state = _state.get();

            switch (state) {
                case 0:
                    throw new RuntimeException(Strings.NOT_STARTED);
                case STARTING_SCHEDULED:
                case RUNNING_SCHEDULED:
                case SCHEDULED:
                    return true;
                case STARTING: {
                    if (_state.compareAndSet(state, STARTING_SCHEDULED))
                        return true;

                    break;
                }
                case IDLE: {
                    if (_state.compareAndSet(state, SCHEDULED)) {
                        startRun();
                        return true;
                    }

                    break;
                }
                case RUNNING: {
                    if (_state.compareAndSet(state, RUNNING_SCHEDULED))
                        return true;

                    break;
                }
                case DISPOSED:
                    return false;
            }
        }
    }

    protected void startRun() {
        // Some implementation override requestRun instead
        throw new IllegalStateException();
    }

    protected final boolean onRunStarting() {
        if (!_state.compareAndSet(SCHEDULED, RUNNING)) {
            if (Debug.ENABLED)
                Debug.assertion(_state.get() == DISPOSED);

            return false;
        }

        return true;
    }

    protected final void onRunEnded() {
        for (;;) {
            if (_state.get() == RUNNING) {
                if (_state.compareAndSet(RUNNING, IDLE))
                    break;
            } else {
                if (Debug.ENABLED)
                    Debug.assertion(_state.get() == RUNNING_SCHEDULED);

                if (_state.compareAndSet(RUNNING_SCHEDULED, SCHEDULED)) {
                    startRun();
                    break;
                }
            }
        }
    }

    protected final boolean isDisposed() {
        return _state.get() == DISPOSED;
    }

    protected final void disposeFromRunThread() {
        if (Debug.ENABLED)
            assertRunning();

        _state.set(DISPOSED);
    }

    protected final boolean disposeFromOtherThread() {
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
                case STARTING_SCHEDULED: {
                    if (_state.compareAndSet(STARTING_SCHEDULED, DISPOSED))
                        return true;

                    break;
                }
                case SCHEDULED: {
                    if (_state.compareAndSet(SCHEDULED, DISPOSED))
                        return true;

                    break;
                }
                case IDLE: {
                    if (_state.compareAndSet(IDLE, DISPOSED))
                        return true;

                    break;
                }
                case RUNNING:
                case RUNNING_SCHEDULED:
                    return false;
                case DISPOSED:
                    return false;
            }
        }
    }

    protected final void setIdle() {
        if (Debug.ENABLED)
            assertRunning();

        _state.set(IDLE);
    }

    protected final void setScheduled() {
        if (Debug.ENABLED)
            assertRunning();

        _state.set(SCHEDULED);
    }

    // Debug

    protected final void assertStarting() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == STARTING);
    }

    protected final void assertScheduled() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == SCHEDULED || state == DISPOSED);
    }

    protected final void assertRunning() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = _state.get();
        Debug.assertion(state == RUNNING || state == RUNNING_SCHEDULED);
    }
}