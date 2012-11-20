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
 * A location that can be the origin of a URI.
 */
public abstract class Origin extends Location {

    private final WeakCache<String, URI> _uris;

    protected Origin(boolean cache) {
        _uris = cache ? null : new WeakCache<String, URI>() {

            @Override
            URI create(String key) {
                return Platform.get().newURI(Origin.this, key);
            }

            @Override
            void onAdded(PlatformRef<URI> value) {
                URI uri = value.get();
                uri.getOrCreate(Origin.this);
                uri.onReferenced(value);
            }
        };
    }

    /**
     * Some implementations can be used either as URI origin or cache.
     */
    @Override
    public boolean isCache() {
        return _uris == null;
    }

    public URI getURI(String path) {
        return _uris.getOrCreate(path);
    }

    final WeakCache<String, URI> uris() {
        return _uris;
    }
}
