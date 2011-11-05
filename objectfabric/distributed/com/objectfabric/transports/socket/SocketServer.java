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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.Connection;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.Strings;
import com.objectfabric.misc.CheckedRunnable;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOListener;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConcurrentMap;
import com.objectfabric.transports.Server;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;
import com.objectfabric.transports.socket.SocketConnection.PhysicalConnection;

/**
 * NIO based socket server. Add filters for TLS or HTTP support.
 */
public class SocketServer extends Server<SocketServer.Session> {

    private final InetAddress _host;

    private final int _port;

    private final AtomicReference<NIOListener> _nioListener = new AtomicReference<NIOListener>();

    private final List<FilterFactory> _filters = new List<FilterFactory>();

    private final PlatformConcurrentMap<Session, Session> _sessions = new PlatformConcurrentMap<Session, Session>();

    public SocketServer(int port) {
        this((InetAddress) null, port);
    }

    public SocketServer(String host, int port) throws UnknownHostException {
        this(InetAddress.getByName(host), port);
    }

    public SocketServer(InetAddress host, int port) {
        _host = host;
        _port = port;

        // First factory corresponds to physical connection
        _filters.add(null);
    }

    public final InetAddress getHost() {
        return _host;
    }

    public final int getPort() {
        return _port;
    }

    //

    public void addFilter(FilterFactory filter) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _filters.add(filter);
    }

    //

    public void start() throws IOException {
        NIOListener listener = new NIOListener() {

            @Override
            public NIOConnection createConnection() {
                PhysicalConnection first = new PhysicalConnection();
                first.init(_filters, 0, false);
                return first;
            }
        };

        if (!_nioListener.compareAndSet(null, listener))
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _filters.add(new FilterFactory() {

            public Filter createFilter(boolean clientSide) {
                if (Debug.ENABLED)
                    setNoTransaction(false);

                Session session = createSession();

                if (Debug.ENABLED)
                    setNoTransaction(true);

                return session.getLastFilter();
            }
        });

        boolean ok = false;

        try {
            listener.start(_host, _port);
            ok = true;
        } finally {
            if (!ok) {
                _filters.clear();
                _nioListener.set(null);
            }
        }
    }

    @Override
    public boolean isStarted() {
        NIOListener listener = _nioListener.get();
        return listener != null;
    }

    public void stop() {
        NIOListener listener = _nioListener.get();

        if (listener == null)
            throw new RuntimeException(Strings.NOT_STARTED);

        listener.stop();

        for (Connection connection : _sessions.keySet())
            connection.close();

        // Wake up the selector in case a session was suspended while writing

        NIOManager.getInstance().wakeup();

        // Sessions must clean themselves

        while (getSessions().size() > 0) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }

        if (Debug.ENABLED)
            for (Object filter : _filters.toArray())
                if (filter instanceof Privileged)
                    assertIdle((Privileged) filter);
    }

    public Set<Session> getSessions() {
        return Collections.unmodifiableSet(_sessions.keySet());
    }

    /**
     * This method is called when a socket connection is established. If the chain of
     * filters identify this connection as part of a new session, it will create a session
     * and notify user callback.
     * <nl>
     * This method is called directly on the NIO thread, not on AsyncOptions's executor.
     * 
     * @param channel
     */
    protected void onConnection(SocketChannel channel) {
    }

    /**
     * Override this method to create your own sessions.
     * <nl>
     * This method is called directly on the NIO thread, not on AsyncOptions's executor.
     */
    protected Session createSession() {
        return new Session(this);
    }

    public static class Session extends SocketConnection {

        private final SocketServer _server;

        public Session(SocketServer server) {
            // Trunk will be replaced by client's once connected
            super(Site.getLocal().getTrunk(), Site.getLocal(), server.getValidator());

            _server = server;
        }

        @Override
        protected void onWriteStarted() {
            super.onWriteStarted();

            _server.onConnection(getSocketChannel());
        }

        @Override
        protected void onDialogEstablished() {
            super.onDialogEstablished();

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _server._sessions.put(this, this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();

            _server.getCallbackExecutor().execute(new CheckedRunnable() {

                @SuppressWarnings("unchecked")
                @Override
                protected void checkedRun() {
                    if (_server.getCallback() != null) {
                        try {
                            ((Callback) _server.getCallback()).onConnection(Session.this);
                        } catch (Exception e) {
                            PlatformAdapter.logUserCodeException(e);
                        }
                    }
                }
            });
        }

        @Override
        protected void onObject(final Object object) {
            super.onObject(object);

            _server.getCallbackExecutor().execute(new CheckedRunnable() {

                @SuppressWarnings("unchecked")
                @Override
                protected void checkedRun() {
                    if (_server.getCallback() != null) {
                        try {
                            ((Callback) _server.getCallback()).onReceived(Session.this, object);
                        } catch (Exception e) {
                            PlatformAdapter.logUserCodeException(e);
                        }
                    }
                }
            });
        }

        @Override
        protected void onWriteStopped(final Exception e) {
            super.onWriteStopped(e);

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _server._sessions.remove(this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();

            _server.getCallbackExecutor().execute(new CheckedRunnable() {

                @SuppressWarnings("unchecked")
                @Override
                protected void checkedRun() {
                    if (_server.getCallback() != null) {
                        try {
                            ((Callback) _server.getCallback()).onDisconnection(Session.this, e);
                        } catch (Exception user) {
                            PlatformAdapter.logUserCodeException(user);
                        }
                    }
                }
            });
        }
    }
}
