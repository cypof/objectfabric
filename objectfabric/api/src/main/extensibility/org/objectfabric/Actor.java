/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.util.concurrent.atomic.AtomicInteger;

import org.objectfabric.CloseCounter.Callback;

/**
 * Base class for any single-thread components which can receive tasks from other threads.
 * ObjectFabric is structured as a set of actors running on a thread pool.
 */
@SuppressWarnings("serial")
abstract class Actor extends AtomicInteger {

    private static final int STARTING = 0;

    private static final int STARTING_SCHEDULED = 1;

    private static final int IDLE = 2;

    private static final int SCHEDULED = 3;

    private static final int RUNNING = 4;

    private static final int RUNNING_SCHEDULED = 5;

    private static final int CLOSING = 6;

    private static final int CLOSED = 7;

    static abstract class Message {

        abstract void run();
    }

    static abstract class Flush {

        abstract void done();
    }

    private final PlatformConcurrentQueue<Message> _messages = new PlatformConcurrentQueue<Message>();

    private final PlatformConcurrentQueue<Flush> _flushes = new PlatformConcurrentQueue<Flush>();

    private List<Flush> _currentFlushes;

    private Callback _closeCallback;

    final boolean addAndRun(Message message) {
        _messages.add(message);

        if (!requestRun()) {
            _messages.poll();
            return false;
        }

        return true;
    }

    final boolean addAndRun(Flush flush) {
        _flushes.add(flush);

        if (!requestRun()) {
            _flushes.poll();
            return false;
        }

        return true;
    }

    //

    final void runMessages(boolean interrupted) {
        for (;;) {
            Message message = _messages.poll();

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            if (message == null)
                break;

            message.run();
        }

        if (!interrupted) {
            for (;;) {
                Flush flush = _flushes.poll();

                if (flush == null)
                    break;

                if (_currentFlushes == null)
                    _currentFlushes = new List<Flush>();

                _currentFlushes.add(flush);
            }
        }
    }

    // State machine

    final void readState() {
        get();
    }

    final void volatileWrite() {
        if (Debug.ENABLED)
            Debug.assertion(get() == STARTING);

        set(STARTING);
    }

    final void onStarted() {
        if (!compareAndSet(STARTING, IDLE)) {
            if (Debug.ENABLED)
                Debug.assertion(get() == STARTING_SCHEDULED);

            set(IDLE);
            requestRun();
        }
    }

    /*
     * TODO: split into request for a message and request for full run. Should help
     * staying on same thread for work stealing thread pools. Also would allow running
     * messages while waiting on write acknowledgment.
     */
    final boolean requestRun() {
        for (;;) {
            int state = get();

            switch (state) {
                case STARTING_SCHEDULED:
                case RUNNING_SCHEDULED:
                case SCHEDULED:
                    return true;
                case STARTING: {
                    if (compareAndSet(state, STARTING_SCHEDULED))
                        return true;

                    break;
                }
                case IDLE: {
                    if (compareAndSet(state, SCHEDULED)) {
                        execute();
                        return true;
                    }

                    break;
                }
                case RUNNING: {
                    if (compareAndSet(state, RUNNING_SCHEDULED))
                        return true;

                    break;
                }
                case CLOSING:
                case CLOSED:
                    return false;
                default:
                    throw new IllegalStateException("" + get());
            }
        }
    }

    final boolean setScheduled() {
        if (Debug.ENABLED) {
            Debug.assertion(Platform.get().value() == Platform.GWT);
            Debug.assertion(get() == IDLE || get() == SCHEDULED);
        }

        if (get() == SCHEDULED)
            return true;

        set(SCHEDULED);
        return false;
    }

    private final void execute() {
        Object key;

        if (Debug.ENABLED)
            ThreadAssert.suspend(key = new Object());

        enqueue();

        if (Debug.ENABLED)
            ThreadAssert.resume(key);
    }

    abstract void enqueue();

    final boolean onRunStarting() {
        if (!compareAndSet(SCHEDULED, RUNNING)) {
            if (Debug.ENABLED) {
                int state = get();
                Debug.assertion(state == CLOSING || state == CLOSED);
            }

            return false;
        }

        return true;
    }

    final void onRunEnded(boolean interrupted) {
        if (!interrupted) {
            if (_currentFlushes != null && _currentFlushes.size() > 0) {
                for (int i = 0; i < _currentFlushes.size(); i++)
                    _currentFlushes.get(i).done();

                _currentFlushes.clear();
            }
        }

        for (;;) {
            int state = get();

            switch (state) {
                case RUNNING: {
                    if (compareAndSet(state, IDLE))
                        return;

                    break;
                }
                case RUNNING_SCHEDULED: {
                    if (compareAndSet(state, SCHEDULED)) {
                        execute();
                        return;
                    }

                    break;
                }
                case CLOSING: {
                    close();
                    return;
                }
                default:
                    throw new IllegalStateException("" + get());
            }
        }
    }

    final void requestClose(Callback callback) {
        if (Debug.ENABLED)
            Debug.assertion(_closeCallback == null);

        _closeCallback = callback;

        for (;;) {
            int state = get();

            switch (state) {
                case STARTING:
                case STARTING_SCHEDULED:
                case SCHEDULED:
                case IDLE: {
                    if (compareAndSet(state, CLOSING)) {
                        close();
                        return;
                    }

                    break;
                }
                case RUNNING:
                case RUNNING_SCHEDULED: {
                    if (compareAndSet(state, CLOSING))
                        return;

                    break;
                }
                case CLOSING:
                case CLOSED: {
                    return;
                }
                default:
                    throw new IllegalStateException("" + get());
            }
        }
    }

    private final void close() {
        OverrideAssert.add(this);
        onClose(_closeCallback);
        OverrideAssert.end(this);

        if (Debug.ENABLED)
            Debug.assertion(get() == CLOSING);

        set(CLOSED);

        // To assert messages do not accumulate after close
        _messages.clear();
    }

    void onClose(Callback callback) {
        OverrideAssert.set(this);

        if (callback != null)
            callback.call();
    }

    final boolean isStarting() {
        int state = get();
        return state == STARTING || state == STARTING_SCHEDULED;
    }

    final boolean isScheduled() {
        int state = get();
        return state == SCHEDULED;
    }

    final boolean isRunning() {
        int state = get();
        return state == RUNNING || state == RUNNING_SCHEDULED;
    }

    final boolean isClosingOrClosed() {
        int state = get();
        return state == CLOSING || state == CLOSED;
    }

    final boolean isClosed() {
        return get() == CLOSED;
    }

    @Override
    public String toString() {
        return Platform.get().defaultToString(this);
    }

    // Debug

    final void assertNoMessages() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        Message message = _messages.poll();

        if (message != null) {
            Debug.fail();
            message.run();
        }

        Flush flush = _flushes.poll();

        if (flush != null) {
            Debug.fail();
            flush.done();
        }
    }

    final void assertStarting() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = get();
        Debug.assertion(state == STARTING);
    }

    final void assertScheduled() {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        int state = get();
        Debug.assertion(state == SCHEDULED || state == CLOSING || state == CLOSED);
    }
}
