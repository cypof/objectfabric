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

import java.util.concurrent.atomic.AtomicReference;

final class URIResolver {

    private final AtomicReference<URIHandler[]> _handlers = new AtomicReference<URIHandler[]>();

    private final PlatformConcurrentMap<Origin, Origin> _origins = new PlatformConcurrentMap<Origin, Origin>();

    private final AtomicReference<Location[]> _caches = new AtomicReference<Location[]>();

    static final int MAX_CACHES = 31, ORIGIN_BIT = Integer.MIN_VALUE;

    private final AtomicReference<Object> _closing = new AtomicReference<Object>();

    // TODO handle removals?

    final PlatformConcurrentMap<Origin, Origin> origins() {
        return _origins;
    }

    final boolean isClosing() {
        return _closing.get() != null;
    }

    final AtomicReference<Object> closing() {
        return _closing;
    }

    //

    final URIHandler[] uriHandlers() {
        return _handlers.get();
    }

    final void addURIHandler(URIHandler handler) {
        checkURIHandler(handler);

        for (;;) {
            URIHandler[] expect = _handlers.get();
            URIHandler[] update = Helper.add(expect, handler);

            if (_handlers.compareAndSet(expect, update))
                break;
        }
    }

    final void addURIHandler(int index, URIHandler handler) {
        checkURIHandler(handler);

        for (;;) {
            URIHandler[] expect = _handlers.get();
            URIHandler[] update = Helper.add(index, expect, handler);

            if (_handlers.compareAndSet(expect, update))
                break;
        }
    }

    private final void checkURIHandler(URIHandler handler) {
        if (handler instanceof Location)
            if (((Location) handler).isCache())
                throw new IllegalArgumentException(Strings.LOCATION_IS_CACHE);

        if (_closing.get() != null)
            throw new ClosedException();
    }

    final URI resolve(Address address, String path) {
        URIHandler[] handlers = _handlers.get();

        if (handlers != null) {
            for (int i = 0; i < handlers.length; i++) {
                URI uri = handlers[i].handle(address, path);

                if (uri != null) {
                    _origins.put(uri.origin(), uri.origin());
                    return uri;
                }
            }
        }

        return null;
    }

    //

    final Location[] caches() {
        return _caches.get();
    }

    final void addCache(Location location) {
        if (!location.isCache())
            throw new IllegalArgumentException(Strings.LOCATION_IS_NOT_CACHE);

        if (_closing.get() != null)
            throw new ClosedException();

        for (;;) {
            Location[] expect = _caches.get();

            if (expect != null && expect.length == MAX_CACHES)
                throw new RuntimeException(Strings.MAX_CACHES);

            Location[] update = Helper.add(expect, location);

            if (_caches.compareAndSet(expect, update))
                break;
        }
    }
}
