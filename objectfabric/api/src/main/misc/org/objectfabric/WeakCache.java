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

import java.util.Iterator;

@SuppressWarnings({ "unchecked", "rawtypes" })
abstract class WeakCache<K, V> {

    private final PlatformConcurrentMap<K, Ref> _map = new PlatformConcurrentMap<K, Ref>();

    abstract V create(K key);

    abstract void onAdded(PlatformRef<V> ref);

    final V get(K key) {
        for (;;) {
            Ref current = _map.get(key);

            if (current == null)
                return null;

            V value = (V) current.get();

            if (value != null)
                return value;
        }
    }

    final V getOrCreate(K key) {
        V created = null;

        for (;;) {
            Ref current = _map.get(key);

            if (current != null) {
                V value = (V) current.get();

                if (value != null)
                    return value;
            }

            if (created == null) {
                created = create(key);

                if (Debug.ENABLED)
                    Debug.assertion(created != null);
            }

            V value = created;
            Ref update = new Ref(key, value, _map);

            if (current != null && _map.replace(key, current, update)) {
                onAdded(update);
                return value;
            }

            current = _map.putIfAbsent(key, update);

            if (current != null)
                value = (V) current.get();
            else
                onAdded(update);

            if (value != null)
                return value;
        }
    }

    final void remove(K key) {
        Ref ref = _map.remove(key);

        if (ref != null)
            ref.clear();
    }

    final Iterable<V> values() {
        return new Iterable<V>() {

            @Override
            public Iterator<V> iterator() {
                return new It<V>(_map.values().iterator());
            }
        };
    }

    private static class It<T> implements Iterator<T> {

        private Iterator _values;

        private T _next;

        It(Iterator values) {
            _values = values;
        }

        @Override
        public boolean hasNext() {
            while (_values.hasNext()) {
                Ref ref = (Ref) _values.next();
                _next = (T) ref.get();

                if (_next != null)
                    return true;
            }

            return false;
        }

        @Override
        public T next() {
            return _next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Ref extends PlatformRef {

        private final Object _key;

        private final PlatformConcurrentMap _map;

        Ref(Object key, Object value, PlatformConcurrentMap map) {
            super(value, Platform.get().getReferenceQueue());

            _key = key;
            _map = map;
        }

        @Override
        void collected() {
            _map.remove(_key, this);
        }
    }

    // Testing

    final int getInternalMapSize() {
        return _map.size();
    }
}
