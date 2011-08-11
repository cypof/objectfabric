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

package com.objectfabric.misc;

public final class PlatformAsyncCallback implements AsyncCallback {

    public final AsyncCallback Callback;

    public final Object State;

    public PlatformAsyncCallback(AsyncCallback callback, Object state) {
        Callback = callback;
        State = state;
    }

    public void onSuccess(Object result) {
        throw new IllegalStateException();
    }

    public void onFailure(Throwable t) {
        throw new IllegalStateException();
    }

    public static final AsyncCallback getCallBack(AsyncCallback value) {
        return value;
    }

    public static final Object getState(AsyncCallback value) {
        return null;
    }
}
