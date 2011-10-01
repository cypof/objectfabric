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
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.Validator;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.NIOManager;

/**
 * Bidirectional communication using polling. This transport can be used for polling over
 * a servlet in case the Comet transport cannot be used.
 */
public class PollingServer extends Privileged {

    private Validator _validator;

    public final Validator getValidator() {
        return _validator;
    }

    public final void setValidator(Validator value) {
        _validator = value;
    }

    /*
     * Tomcat seems to require session attributes to be serializable for clustering
     * support, so marked serializable in case it needs to be stored in a session.
     */
    public static class PollingSession extends Connection implements Serializable {

        private final PollingServer _server;

        public PollingSession(PollingServer server) {
            super(Site.getLocal().getTrunk(), Site.getLocal(), server._validator);

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

            ensureThreadContextBufferLength(NIOManager.SOCKET_BUFFER_SIZE);
            byte[] buffer = getThreadContextBuffer();

            int written = write(buffer, 0, buffer.length);

            if (written < 0)
                written = -written - 1;

            if (Debug.ENABLED)
                setNoTransaction(false);

            byte[] result = new byte[written];
            System.arraycopy(buffer, 0, result, 0, written);
            return result;
        }
    }
}
