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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.Connection;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.Strings;
import com.objectfabric.Validator;
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
public class SocketServer extends Privileged implements Server<SocketConnection> {

    private final InetAddress _host;

    private final int _port;

    private final AtomicReference<NIOListener> _nioListener = new AtomicReference<NIOListener>();

    private final List<FilterFactory> _filters = new List<FilterFactory>();

    private final PlatformConcurrentMap<SocketConnection, SocketConnection> _sessions = new PlatformConcurrentMap<SocketConnection, SocketConnection>();

    private Validator _validator;

    private Callback<SocketConnection> _callback;

    private Executor _callbackExecutor;

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

        _callbackExecutor = getDefaultAsyncOptions().getExecutor();
    }

    public final InetAddress getHost() {
        return _host;
    }

    public final int getPort() {
        return _port;
    }

    //

    public final Validator getValidator() {
        return _validator;
    }

    public final void setValidator(Validator value) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _validator = value;
    }

    //

    public void addFilter(FilterFactory filter) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _filters.add(filter);
    }

    //

    public final Callback<SocketConnection> getCallback() {
        return _callback;
    }

    public final void setCallback(Callback<SocketConnection> value) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _callback = value;
    }

    public final Executor getCallbackExecutor() {
        return _callbackExecutor;
    }

    public final void setCallbackExecutor(Executor value) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _callbackExecutor = value;
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
                Session session = new Session();
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

    public Set<SocketConnection> getSessions() {
        return Collections.unmodifiableSet(_sessions.keySet());
    }

    /**
     * This method is called when a socket connection is established. If the chain of
     * filters identify this connection as part of a new session, it will create a session
     * and notify user callback.
     * <nl>
     * This method is called directly on the NIO thread, not on AsyncOptions's executor.
     */
    protected void onConnection(SocketChannel channel) {
    }

    private final class Session extends SocketConnection {

        public Session() {
            // Trunk will be replaced by client's once connected
            super(Site.getLocal().getTrunk(), Site.getLocal(), _validator);
        }

        @Override
        protected void onWriteStarted() {
            super.onWriteStarted();

            onConnection(getSocketChannel());
        }

        @Override
        protected void onDialogEstablished() {
            super.onDialogEstablished();

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _sessions.put(this, this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();

            getCallbackExecutor().execute(new Runnable() {

                public void run() {
                    if (_callback != null) {
                        try {
                            _callback.onConnection(Session.this);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            });
        }

        @Override
        protected void onObject(final Object object) {
            super.onObject(object);

            getCallbackExecutor().execute(new Runnable() {

                public void run() {
                    if (_callback != null) {
                        try {
                            _callback.onReceived(Session.this, object);
                        } catch (Throwable t) {
                            PlatformAdapter.logListenerException(t);
                        }
                    }
                }
            });
        }

        @Override
        protected void onWriteStopped(final Throwable t) {
            super.onWriteStopped(t);

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _sessions.remove(this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();

            getCallbackExecutor().execute(new Runnable() {

                public void run() {
                    if (_callback != null) {
                        try {
                            _callback.onDisconnection(Session.this, t);
                        } catch (Throwable t_) {
                            PlatformAdapter.logListenerException(t_);
                        }
                    }
                }
            });
        }
    }
}
