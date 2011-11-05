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

import java.util.concurrent.Executor;

import com.objectfabric.Connection;
import com.objectfabric.Privileged;
import com.objectfabric.Strings;
import com.objectfabric.Validator;

public abstract class Server<C extends Connection> extends Privileged {

    public interface Callback<S extends Connection> {

        /**
         * A client has connected.
         */
        void onConnection(S session);

        /**
         * A client has disconnected. Exception might contain additional information about
         * the disconnection.
         */
        void onDisconnection(S session, Exception e);

        /**
         * An object has been sent by the client. Events occurring on it will be
         * replicated until the object is garbage-collected on either site.
         */
        void onReceived(S session, Object object);
    }

    private Callback<?> _callback;

    private Executor _callbackExecutor;

    private Validator _validator;

    protected Server() {
        _callbackExecutor = getDefaultAsyncOptions().getExecutor();
    }

    public abstract boolean isStarted();

    public final Callback<?> getCallback() {
        return _callback;
    }

    public final void setCallback(Callback<?> value) {
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

    public final Validator getValidator() {
        return _validator;
    }

    public final void setValidator(Validator value) {
        if (isStarted())
            throw new RuntimeException(Strings.ALREADY_STARTED);

        _validator = value;
    }
}
