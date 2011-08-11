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

package com.objectfabric;

import java.lang.ref.ReferenceQueue;

import com.objectfabric.misc.PlatformWeakReference;

/**
 * Otherwise TObject.Referene does not compile with javac (?).
 */
class WeakReferenceAccessor<T> extends PlatformWeakReference<T> {

    public WeakReferenceAccessor(T t, ReferenceQueue<T> queue) {
        super(t, queue);
    }
}
