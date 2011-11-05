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

import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.transports.Client.Callback;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

/**
 * Bidirectional communication with server by polling a servlet using GWT-RPC.
 */
public final class ServletClient extends Connection {

    public static final int BUFFER_LENGTH = 16000;

    public static int DEFAULT_POLLING_LATENCY = 100;

    private final ServletServiceAsync _service;

    private final int _pollingLatencyMillis;

    private Poller _poller;

    private AsyncCallback<Void> _connectCallback;

    private Callback _clientCallback;

    public ServletClient() {
        this(DEFAULT_POLLING_LATENCY);
    }

    public ServletClient(int pollingLatencyMillis) {
        super(Site.getLocal().getTrunk(), null, null);

        _pollingLatencyMillis = pollingLatencyMillis;

        _service = (ServletServiceAsync) GWT.create(ServletService.class);

        String staticResponseURL = GWT.getModuleBaseURL();

        if (staticResponseURL.equals(""))
            staticResponseURL = "/";

        ((ServiceDefTarget) _service).setServiceEntryPoint(staticResponseURL + "objectfabric");
    }

    public final Callback getCallback() {
        return _clientCallback;
    }

    public final void setCallback(Callback value) {
        _clientCallback = value;
    }

    public void connectAsync() {
        connectAsync(null);
    }

    public void connectAsync(AsyncCallback<Void> callback) {
        if (_connectCallback != null)
            throw new IllegalStateException("This client is connecting or already connected.");

        _connectCallback = callback;

        _service.connect(new AsyncCallback<byte[]>() {

            public void onSuccess(byte[] uidSeed) {
                PlatformAdapter.initializeUIDGenerator(uidSeed);

                if (Debug.ENABLED)
                    setNoTransaction(true);

                ServletClient.this.startRead();
                ServletClient.this.startWrite();

                if (Debug.ENABLED)
                    setNoTransaction(false);

                _poller = new Poller();
                _poller.scheduleRepeating(_pollingLatencyMillis);
            }

            public void onFailure(Throwable t) {
                Log.write("Failed to connect (" + t + ")");
            }
        });
    }

    public void disconnect() {
        if (_poller != null)
            _poller.dispose(null);

        if (_clientCallback != null)
            _clientCallback.onDisconnected(null);
    }

    @Override
    protected void onDialogEstablished() {
        if (_connectCallback != null) {
            try {
                _connectCallback.onSuccess(null);
            } catch (Throwable ex) {
                PlatformAdapter.logUserCodeException(ex);
            }
        }
    }

    @Override
    protected void onObject(Object object) {
        if (_clientCallback != null)
            _clientCallback.onReceived(object);
    }

    private final class Poller extends Timer {

        private final byte[] _buffer = new byte[BUFFER_LENGTH];

        private boolean _polling;

        public void dispose(Exception e) {
            Transaction current = Transaction.getCurrent();
            Transaction.setCurrent(null);

            if (Debug.ENABLED)
                setNoTransaction(true);

            cancel();
            stopRead(e);
            stopWrite(e);

            if (Debug.ENABLED)
                setNoTransaction(false);

            Transaction.setCurrent(current);
        }

        @Override
        public void run() {
            if (!_polling) {
                _polling = true;

                if (Debug.ENABLED)
                    setNoTransaction(true);

                int written = write(_buffer, 0, _buffer.length);

                if (written < 0)
                    written = -written - 1;

                byte[] temp = new byte[written];
                System.arraycopy(_buffer, 0, temp, 0, written);

                if (Debug.ENABLED)
                    setNoTransaction(false);

                _service.call(temp, new AsyncCallback<byte[]>() {

                    public void onSuccess(byte[] data) {
                        if (data != null) {
                            if (Debug.ENABLED)
                                setNoTransaction(true);

                            read(data, 0, data.length);

                            if (Debug.ENABLED)
                                setNoTransaction(false);
                        }

                        _polling = false;
                    }

                    public void onFailure(Throwable caught) {
                        dispose((Exception) caught);
                        _polling = false;
                    }
                });
            }
        }
    }
}
