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

package com.objectfabric.transports.polling;

import java.io.Serializable;

import com.objectfabric.Connection;
import com.objectfabric.Site;
import com.objectfabric.misc.CheckedRunnable;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformThreadLocal;
import com.objectfabric.transports.Server;

/**
 * Bidirectional communication using polling. This transport can be used for polling over
 * a servlet in case the Comet transport cannot be used.
 */
public class PollingServer<C extends PollingServer.PollingSession> extends Server<C> {

    @Override
    public boolean isStarted() {
        return false;
    }

    /*
     * Tomcat seems to require session attributes to be serializable for clustering
     * support, so marked serializable in case it needs to be stored in a session.
     */
    public static class PollingSession extends Connection implements Serializable {

        private final PollingServer _server;

        private final PlatformThreadLocal<byte[]> _buffer = new PlatformThreadLocal<byte[]>();

        public PollingSession(PollingServer server) {
            super(Site.getLocal().getTrunk(), Site.getLocal(), server.getValidator());

            _server = server;

            if (Debug.ENABLED)
                setNoTransaction(true);

            startRead();
            startWrite();

            if (Debug.ENABLED)
                setNoTransaction(false);
        }

        public final PollingServer getServer() {
            return _server;
        }

        @Override
        public void close_(Exception e) {
            super.close_(e);

            if (Debug.ENABLED)
                setNoTransaction(true);

            stopRead(e);
            stopWrite(e);

            if (Debug.ENABLED)
                setNoTransaction(false);
        }

        public byte[] call(byte[] data) {
            if (Debug.ENABLED)
                setNoTransaction(true);

            read(data, 0, data.length);

            byte[] buffer = _buffer.get();

            if (buffer == null)
                _buffer.set(buffer = new byte[NIOManager.SOCKET_BUFFER_SIZE]);

            int written = write(buffer, 0, buffer.length);

            if (written < 0)
                written = -written - 1;

            if (Debug.ENABLED)
                setNoTransaction(false);

            byte[] result = new byte[written];
            System.arraycopy(buffer, 0, result, 0, written);
            return result;
        }

        @Override
        protected void onDialogEstablished() {
            super.onDialogEstablished();

            _server.getCallbackExecutor().execute(new CheckedRunnable() {

                @SuppressWarnings("unchecked")
                @Override
                protected void checkedRun() {
                    if (_server.getCallback() != null) {
                        try {
                            _server.getCallback().onConnection(PollingSession.this);
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
                            _server.getCallback().onReceived(PollingSession.this, object);
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

            _server.getCallbackExecutor().execute(new CheckedRunnable() {

                @SuppressWarnings("unchecked")
                @Override
                protected void checkedRun() {
                    if (_server.getCallback() != null) {
                        try {
                            _server.getCallback().onDisconnection(PollingSession.this, e);
                        } catch (Exception user) {
                            PlatformAdapter.logUserCodeException(user);
                        }
                    }
                }
            });
        }
    }
}
