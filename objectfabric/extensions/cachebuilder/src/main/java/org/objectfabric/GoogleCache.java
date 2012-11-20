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

import java.util.concurrent.ConcurrentMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * Memory cache backed by Google CacheBuilder.
 */
public class GoogleCache extends Memory {

    public GoogleCache() {
        this(CacheBuilder.newBuilder());
    }

    @SuppressWarnings("unchecked")
    public GoogleCache(CacheBuilder builder) {
        super(true, new GoogleCacheBackend(builder.build().asMap()));

        builder.removalListener(new RemovalListener() {

            @Override
            public void onRemoval(RemovalNotification notification) {
                onEviction(notification.getValue());
            }
        });
    }

    private static final class GoogleCacheBackend implements Backend {

        final ConcurrentMap _map;

        GoogleCacheBackend(ConcurrentMap map) {
            _map = map;
        }

        @Override
        public Object get(String key) {
            return _map.get(key);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object putIfAbsent(String key, Object value) {
            return _map.putIfAbsent(key, value);
        }
    }
}