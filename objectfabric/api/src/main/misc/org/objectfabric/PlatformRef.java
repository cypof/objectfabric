/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

@SuppressWarnings({ "unchecked", "rawtypes" })
abstract class PlatformRef<T> extends WeakReference<T> {

    PlatformRef(T referent, Object queue) {
        super(referent, (ReferenceQueue) queue);
    }

    abstract void collected();
}
