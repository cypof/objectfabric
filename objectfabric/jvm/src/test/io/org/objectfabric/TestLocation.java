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

public class TestLocation extends Origin implements URIHandler {

    private URI _uri;

    TestLocation() {
        super(false);
    }

    final void setURI(URI uri) {
        _uri = uri;
    }

    @Override
    public URI handle(Address address, String path) {
        return _uri;
    }

    @Override
    View newView(URI _) {
        return new View(this) {

            @Override
            void getKnown(URI uri) {
            }

            @Override
            void onKnown(URI uri, long[] ticks) {
            }

            @Override
            void getBlock(URI uri, long tick) {
            }

            @Override
            void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
            }
        };
    }
}
