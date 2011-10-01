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

import com.objectfabric.Privileged;

public abstract class CheckedRunnable extends Privileged implements Runnable {

    protected abstract void checkedRun();

    public final void run() {
        try {
            checkedRun();
        } catch (Throwable t) {
            onThrowable(t);
        }
    }
}
