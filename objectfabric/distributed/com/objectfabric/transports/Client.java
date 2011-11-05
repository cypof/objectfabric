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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import com.objectfabric.AsyncOptions;
import com.objectfabric.misc.AsyncCallback;

public interface Client extends Closeable {

    public interface Callback {

        /**
         * An object has been sent by the server. Events occurring on it will be
         * replicated until the object is garbage-collected on either site.
         */
        void onReceived(Object object);

        /**
         * Connection has been lost with remote site. Exception might contain additional
         * information about the disconnection.
         */
        void onDisconnected(Exception e);
    }

    Callback getCallback();

    void setCallback(Callback value);

    void connect() throws IOException;

    Future<Void> connectAsync(AsyncCallback<Void> callback);

    Future<Void> connectAsync(AsyncCallback<Void> callback, AsyncOptions asyncOptions);

    /**
     * Connects and blocks until received a first message from the server. This is handy
     * for applications that share one root object between client and server.
     */
    Object connectAndWaitObject() throws IOException;

    void close();
}
