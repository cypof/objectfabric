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
 * Where resources can be loaded from and stored to.
 */
public abstract class Location {

    protected Location() {
    }

    /**
     * URI origin or cache. Some implementations can be used for either.
     */
    public boolean isCache() {
        return true;
    }

    //

    abstract View newView(URI uri);

    void onView(URI uri, View view) {
    }

    //

    Clock newClock(Watcher watcher) {
        return null;
    }

    void sha1(SHA1Digest sha1) {
    }
}
