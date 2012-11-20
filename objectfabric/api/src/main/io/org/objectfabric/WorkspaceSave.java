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

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("serial")
abstract class WorkspaceSave extends AtomicBoolean {

    static abstract class Callback {

        abstract void run(long tick, byte[] range, byte id);
    }

    final void run(URIResolver resolver) {
        boolean active = false;

        active |= start(resolver.uriHandlers(), active);
        active |= start(resolver.caches(), active);

        for (Location location : resolver.origins().keySet())
            active |= location.start(this);

        if (!active) {
            run(new Callback() {

                @Override
                void run(long tick, byte[] range, byte id) {
                    WorkspaceLoad.recycle(tick, range, id);
                }
            });

            done();
        }
    }

    private final boolean start(Object[] array, boolean active) {
        if (array != null)
            for (int i = 0; i < array.length; i++)
                if (array[i] instanceof Location)
                    active |= ((Location) array[i]).start(this);

        return active;
    }

    final boolean start() {
        return compareAndSet(false, true);
    }

    abstract void run(Callback callback);

    void done() {
    }
}