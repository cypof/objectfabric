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

/**
 * A view is a resource as seen by a location.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
abstract class View {

    private final Location _location;

    View(Location location) {
        _location = location;
    }

    final Location location() {
        return _location;
    }

    //

    abstract void getKnown(URI uri);

    abstract void onKnown(URI uri, long[] ticks);

    abstract void getBlock(URI uri, long tick);

    abstract void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested);

    //

    void open(URI uri) {
        if (Debug.ENABLED)
            Platform.get().assertLock(uri, true);
    }

    void close(URI uri) {
        if (Debug.ENABLED)
            Platform.get().assertLock(uri, true);
    }
}
