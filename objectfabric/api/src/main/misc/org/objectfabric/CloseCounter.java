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

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({ "rawtypes", "unchecked" })
abstract class CloseCounter {

    interface Callback {

        void call();
    }

    private final PlatformConcurrentMap _map = new PlatformConcurrentMap();

    private final AtomicInteger _counter = new AtomicInteger();

    CloseCounter() {
        _map.put(this, this);
        _counter.set(1);
    }

    abstract void done();

    final Callback startOne(final Object o) {
        if (Debug.ENABLED)
            Debug.assertion(!_map.isEmpty());

        Object previous = _map.put(o, o);
        int value = _counter.getAndIncrement();

        if (Debug.ENABLED)
            Debug.assertion(previous == null && value > 0);

        return new Callback() {

            @Override
            public void call() {
                endOne(o);
            }
        };
    }

    final void onAllStarted() {
        endOne(this);
    }

    private final void endOne(Object o) {
        Object value = _map.remove(o);

        if (Debug.ENABLED)
            Debug.assertion(value != null);

        if (_counter.decrementAndGet() == 0) {
            if (Debug.ENABLED)
                Debug.assertion(_map.isEmpty());

            done();
        }
    }
}