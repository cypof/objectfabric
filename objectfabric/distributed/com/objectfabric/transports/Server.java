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

package com.objectfabric.transports;

import java.io.IOException;
import java.util.Set;

import com.objectfabric.Connection;

public interface Server<C extends Connection> {

    public interface Callback<C> {

        /**
         * A client has connected.
         */
        void onConnection(C session);

        /**
         * A client has disconnected. Exception might contain additional information about
         * the disconnection.
         */
        void onDisconnection(C session, Throwable t);

        /**
         * An object has been sent by the client. Events occurring on it will be
         * replicated until the object is garbage-collected on either site.
         */
        void onReceived(C session, Object object);
    }

    Callback getCallback();

    void setCallback(Callback value);

    void start() throws IOException;

    void stop();

    Set<C> getSessions();
}
