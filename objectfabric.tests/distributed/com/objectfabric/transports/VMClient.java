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

import java.util.concurrent.Future;

import com.objectfabric.AsyncOptions;
import com.objectfabric.Site;
import com.objectfabric.misc.AsyncCallback;

public class VMClient extends VMConnection {

    private final ClientState _state = new ClientState();

    public VMClient() {
        super(Site.getLocal().getTrunk(), null, null);
    }

    public Future<Void> connectAsync() {
        return connectAsync(null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback) {
        return connectAsync(callback, null);
    }

    public Future<Void> connectAsync(AsyncCallback<Void> callback, AsyncOptions options) {
        return _state.startConnection(callback, options);
    }

    @Override
    protected void onDialogEstablished() {
        _state.onDialogEstablished();
    }
}
