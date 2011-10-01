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

package com.objectfabric.transports.http;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.objectfabric.AsyncOptions;
import com.objectfabric.Connection;
import com.objectfabric.Site;
import com.objectfabric.Transaction;
import com.objectfabric.Validator;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.CheckedRunnable;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.http.CometTransport.HTTPRequestBase;

abstract class HTTPConnection extends Connection {

    private final CometTransport _transport;

    private final ClientState _state = new ClientState();

    private final AtomicBoolean _connected = new AtomicBoolean();

    private Callback _callback;

    private Executor _callbackExecutor;

    protected HTTPConnection(final Object url, Validator validator, boolean encoded) {
        super(Site.getLocal().getTrunk(), null, validator);

        _transport = new CometTransport(encoded) {

            @Override
            protected HTTPRequestBase createRequest(boolean serverToClient) {
                return HTTPConnection.this.createRequest(url, serverToClient);
            }

            @Override
            protected void read(byte[] buffer, int offset, int limit) {
                if (!_connected.get() && _connected.compareAndSet(false, true))
                    start();

                if (Debug.ENABLED)
                    setNoTransaction(true);

                HTTPConnection.this.read(buffer, offset, limit);

                if (Debug.ENABLED)
                    setNoTransaction(false);
            }

            @Override
            protected int write(byte[] buffer, int offset, int limit) {
                if (Debug.ENABLED)
                    setNoTransaction(true);

                int written = HTTPConnection.this.write(buffer, offset, limit);

                if (Debug.ENABLED)
                    setNoTransaction(false);

                return written;
            }

            @Override
            protected void onError(Exception e) {
                HTTPConnection.this.close(e);
            }
        };

        _callbackExecutor = getDefaultAsyncOptions().getExecutor();
    }

    //

    public final Callback getCallback() {
        return _callback;
    }

    public final void setCallback(Callback value) {
        _callback = value;
    }

    public final Executor getCallbackExecutor() {
        return _callbackExecutor;
    }

    public final void setCallbackExecutor(Executor value) {
        _callbackExecutor = value;
    }

    //

    public void connect() throws IOException {
        Future<Void> future = connectAsync(null);

        try {
            future.get();
        } catch (java.lang.InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            if (ex.getCause() instanceof IOException)
                throw (IOException) ex.getCause();

            throw PlatformAdapter.createIOException((Exception) ex.getCause());
        }
    }

    public Future<Void> connectAsync() {
        return connectAsync(null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback) {
        return connectAsync(callback, null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback, AsyncOptions asyncOptions) {
        ClientFuture future = _state.startConnection(callback, asyncOptions);
        _transport.connect();
        return future;
    }

    public boolean isConnected() {
        return _connected.get();
    }

    @Override
    protected void close_(Exception e) {
        super.close_(e);

        _transport.close();

        if (_connected.get() && _connected.compareAndSet(true, false))
            stop(e);
    }

    @Override
    protected void onDialogEstablished() {
        _state.onDialogEstablished();
    }

    @Override
    protected void requestWrite() {
        _transport.requestWrite();
    }

    @Override
    protected void onObject(final Object object) {
        super.onObject(object);

        getCallbackExecutor().execute(new CheckedRunnable() {

            @Override
            protected void checkedRun() {
                if (_callback != null) {
                    try {
                        _callback.onReceived(object);
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

        getCallbackExecutor().execute(new CheckedRunnable() {

            @Override
            protected void checkedRun() {
                if (_callback != null) {
                    try {
                        _callback.onDisconnected(e);
                    } catch (Exception user) {
                        PlatformAdapter.logUserCodeException(user);
                    }
                }
            }
        });
    }

    protected abstract HTTPRequestBase createRequest(Object url, boolean serverToClient);

    private void start() {
        if (Debug.ENABLED)
            setNoTransaction(true);

        startRead();
        startWrite();

        if (Debug.ENABLED)
            setNoTransaction(false);
    }

    private void stop(Exception e) {
        Transaction current = Transaction.getCurrent();
        Transaction.setCurrent(null);

        if (Debug.ENABLED)
            setNoTransaction(true);

        stopRead(e);
        stopWrite(e);

        if (Debug.ENABLED)
            setNoTransaction(false);

        Transaction.setCurrent(current);
    }
}
