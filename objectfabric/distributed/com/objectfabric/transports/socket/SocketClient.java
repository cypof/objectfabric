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
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.objectfabric.AsyncOptions;
import com.objectfabric.Site;
import com.objectfabric.Validator;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.List;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.TransparentExecutor;
import com.objectfabric.transports.Client;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;

public class SocketClient extends SocketConnection implements Client {

    private final InetAddress _host;

    private final int _port;

    private final ClientState _state = new ClientState();

    private final List<FilterFactory> _filters = new List<FilterFactory>();

    private Callback _callback;

    private Executor _callbackExecutor;

    public SocketClient(String host, int port) throws UnknownHostException {
        this(host, port, null);
    }

    public SocketClient(InetAddress host, int port) {
        this(host, port, null);
    }

    public SocketClient(String host, int port, Validator validator) throws UnknownHostException {
        this(InetAddress.getByName(host), port, validator);
    }

    public SocketClient(InetAddress host, int port, Validator validator) {
        super(Site.getLocal().getTrunk(), null, validator);

        _host = host;
        _port = port;

        // First factory corresponds to physical connection
        _filters.add(null);

        _callbackExecutor = getDefaultAsyncOptions().getExecutor();
    }

    public void addFilter(FilterFactory filter) {
        _state.throwIfAlreadyStarted();
        _filters.add(filter);
    }

    public final Callback getCallback() {
        return _callback;
    }

    public final void setCallback(Callback value) {
        _state.throwIfAlreadyStarted();
        _callback = value;
    }

    public final Executor getCallbackExecutor() {
        return _callbackExecutor;
    }

    public final void setCallbackExecutor(Executor value) {
        _state.throwIfAlreadyStarted();
        _callbackExecutor = value;
    }

    //

    public void connect() throws IOException {
        @SuppressWarnings("unchecked")
        Future<Void> future = connectAsync(getNopCallback_objectfabric());

        try {
            future.get();
        } catch (java.lang.InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw PlatformAdapter.createIOException(ex.getCause());
        }
    }

    public Future<Void> connectAsync() {
        return connectAsync(null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback) {
        return connectAsync(callback, null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback, AsyncOptions asyncOptions) {
        final ClientFuture future = _state.startConnection(callback, asyncOptions);

        _filters.add(new FilterFactory() {

            public Filter createFilter(boolean clientSide) {
                return getLastFilter();
            }
        });

        // Build filter chain
        PhysicalConnection first = new PhysicalConnection();
        first.init(_filters, 0, true);

        Future<Void> socketFuture = NIOManager.getInstance().connect(first, _host, _port, new AsyncCallback<Void>() {

            @SuppressWarnings("unchecked")
            public void onSuccess(Void result) {
            }

            public void onFailure(Throwable t) {
                future.setException(t);
            }
        }, new AsyncOptions() {

            @Override
            public Executor getExecutor() {
                return TransparentExecutor.getInstance();
            }
        });

        future.setTransportFuture(socketFuture);
        return future;
    }

    //

    @Override
    protected void onDialogEstablished() {
        _state.onDialogEstablished();
    }

    @Override
    protected void onObject(final Object object) {
        super.onObject(object);

        getCallbackExecutor().execute(new Runnable() {

            public void run() {
                if (_callback != null) {
                    try {
                        _callback.onReceived(object);
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

        getCallbackExecutor().execute(new Runnable() {

            public void run() {
                if (_callback != null) {
                    try {
                        _callback.onDisconnected(t);
                    } catch (Throwable t_) {
                        PlatformAdapter.logListenerException(t_);
                    }
                }
            }
        });
    }
}
