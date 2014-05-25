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

import org.objectfabric.Actor.Message;

/**
 * Network location.
 */
@SuppressWarnings("rawtypes")
public abstract class Remote extends Origin {

    public enum Status {
        DISCONNECTED, CONNECTING, WAITING_RETRY, SYNCHRONIZING, UP_TO_DATE
    }

    public static final String TCP = "tcp", SSL = "ssl", WS = "ws", WSS = "wss";

    public static final String WS_PATH = "/websocket";

    private static final long NO_RETRY = -1;

    private final Address _address;

    private final Run _run = new Run();

    private int _actives;

    private ConnectionAttempt _attempt;

    private String _info;

    private long _lastAttempt;

    private volatile Connection _connection;

    protected Remote(boolean cache, Address address) {
        super(cache);

        _address = address;

        _run.onStarted();
    }

    final Address address() {
        return _address;
    }

    final Connection connection() {
        return _connection;
    }

    // TODO listener
    public final Status status() {
        if (_connection != null)
            return InFlight.idle() ? Status.UP_TO_DATE : Status.SYNCHRONIZING;

        if (_actives == 0 || _lastAttempt == NO_RETRY || !ClientURIHandler.isEnabled())
            return Status.DISCONNECTED;

        if (_attempt != null)
            return Status.CONNECTING;

        return Status.WAITING_RETRY;
    }

    public final String statusInfo() {
        return _info;
    }

    //

    @Override
    View newView(URI uri) {
        return new ClientView(this);
    }

    final void onOpen(final URI uri) {
        _run.addAndRun(new Message() {

            @Override
            void run() {
                invariants();

                if (_connection != null)
                    _connection.postSubscribe(uri);

                if (_actives++ == 0 && _lastAttempt != NO_RETRY)
                    retry();

                invariants();
            }
        });
    }

    final void onClose(final URI uri) {
        _run.addAndRun(new Message() {

            @Override
            void run() {
                invariants();

                // TODO grace period
                if (--_actives == 0) {
                    if (_connection != null) {
                        _connection.requestClose(null);
                        _connection = null;
                    } else
                        closeAttempt();
                }

                invariants();
            }
        });
    }

    /*
     * 
     */

    interface ConnectionAttempt {

        void start();

        void cancel();
    }

    abstract ConnectionAttempt createAttempt();

    private final void closeAttempt() {
        if (_attempt != null) {
            _attempt.cancel();
            _attempt = null;
        }
    }

    //

    final void retry() {
        _run.addAndRun(new Message() {

            @Override
            void run() {
                invariants();
                ConnectionAttempt toStart = null;
                closeAttempt();

                if (_actives > 0) {
                    if (_connection == null) {
                        _attempt = toStart = createAttempt();
                        _lastAttempt = Platform.get().approxTimeMs();

                        if (!Debug.COMMUNICATIONS_DISABLE_TIMERS) {
                            Platform.get().schedule(new Runnable() {

                                @Override
                                public void run() {
                                    onError(null, Strings.TIMEOUT, true);
                                }
                            }, 4000);
                        }
                    }
                }

                invariants();

                if (toStart != null)
                    toStart.start();
            }
        });
    }

    final void onConnection(final Connection connection) {
        if (Debug.ENABLED)
            Debug.assertion(connection != null);

        _run.addAndRun(new Message() {

            @SuppressWarnings("null")
            @Override
            void run() {
                invariants();

                if (_attempt == null || _actives == 0)
                    connection.requestClose(null);
                else {
                    _attempt = null;
                    _connection = connection;

                    if (!isCache()) {
                        for (final URI uri : uris().values()) {
                            uri.runIf(new Runnable() {

                                @Override
                                public void run() {
                                    connection.postSubscribe(uri);
                                }
                            }, true);
                        }
                    }
                }

                invariants();
            }
        });
    }

    final void unsubscribe(final URI uri) {
        _run.addAndRun(new Message() {

            @Override
            void run() {
                if (_connection != null) {
                    uri.runIf(new Runnable() {

                        @Override
                        public void run() {
                            _connection.postUnsubscribe(uri);
                        }
                    }, false);
                }
            }
        });
    }

    final void onError(final Connection connection, final String info, final boolean canRetry) {
        if (Debug.ENABLED)
            Debug.assertion(info != null);

        _run.addAndRun(new Message() {

            @Override
            void run() {
                invariants();
                long now = 0, next = 0;
                boolean ignore = true;

                if (connection == null && _attempt != null) {
                    closeAttempt();
                    ignore = false;
                }

                if (connection != null && connection == _connection) {
                    connection.requestClose(null);
                    _connection = null;
                    ignore = false;
                }

                if (!ignore) {
                    if (!canRetry)
                        _lastAttempt = NO_RETRY;

                    if (_actives > 0 && _lastAttempt != NO_RETRY) {
                        now = Platform.get().approxTimeMs(); // try every rand(8s)
                        next = _lastAttempt + Platform.get().randomInt(8192);
                    }

                    _info = info;
                }

                if (next == 0) {
                    if (connection != null)
                        Log.write(this + ": " + info);
                } else {
                    if (!Debug.COMMUNICATIONS_DISABLE_TIMERS) {
                        int delay = next <= now ? 0 : (int) (next - now);

                        Platform.get().schedule(new Runnable() {

                            @Override
                            public void run() {
                                retry();
                            }
                        }, delay);
                    }
                }

                invariants();
            }
        });
    }

    @SuppressWarnings("serial")
    private final class Run extends Actor implements Runnable {

        @Override
        protected void enqueue() {
            Platform.get().execute(this);
        }

        @Override
        public void run() {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            onRunStarting();
            runMessages(false);

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    //

    abstract Headers headers();

    //

    @Override
    void sha1(SHA1Digest sha1) {
        sha1.update(_address.Scheme);
        sha1.update(_address.Host);
        sha1.update((byte) (_address.Port >> 8));
        sha1.update((byte) (_address.Port >> 0));
    }

    @Override
    public String toString() {
        String s = "";

        if (Debug.ENABLED)
            s += Platform.get().defaultToString(this) + "-";

        return s + _address.toString();
    }

    // Debug

    private final void invariants() {
        if (Debug.ENABLED) {
            if (_actives == 0)
                Debug.assertion(_attempt == null && _connection == null);
            else
                Debug.assertion(_attempt == null || _connection == null);
        }
    }
}
