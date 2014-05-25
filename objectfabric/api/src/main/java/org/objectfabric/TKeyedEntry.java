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

import java.util.Map;

@SuppressWarnings("rawtypes")
final class TKeyedEntry<K, V> implements Map.Entry<K, V> {

    static final Object REMOVAL = new Object() {

        @Override
        public String toString() {
            return "REMOVAL";
        }
    };

    static final Object READ = new Object() {

        @Override
        public String toString() {
            return "READ";
        }
    };

    static final TKeyedEntry REMOVED = new TKeyedEntry();

    private final K _key;

    private final int _hash;

    private final V _value;

    TKeyedEntry(K key, int hash, V value) {
        if (Debug.ENABLED) {
            Debug.assertion(key != null && !(key instanceof TObject.Version));
            Debug.assertion(!(value instanceof TObject.Version));
        }

        _key = key;
        _hash = hash;
        _value = value;
    }

    private TKeyedEntry() {
        _key = null;
        _hash = 0;
        _value = null;
    }

    final int getHash() {
        return _hash;
    }

    final boolean isRemoval() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        return _value == REMOVAL;
    }

    final boolean isRemovalNoCheck() {
        return _value == REMOVAL;
    }

    final boolean isRead() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        boolean result = _value == READ;

        if (Debug.ENABLED && result)
            Debug.assertion(!isRemoval());

        return result;
    }

    @Override
    public K getKey() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        return _key;
    }

    @Override
    public V getValue() {
        if (Debug.ENABLED)
            Debug.assertion(!isRemoval() && this != REMOVED);

        return _value;
    }

    final V getValueOrRemoval() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        return _value;
    }

    @Override
    public V setValue(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Map.Entry))
            return false;

        Map.Entry e = (Map.Entry) o;

        Object k1 = getKey();
        Object k2 = e.getKey();

        if (k1 == k2 || k1.equals(k2)) {
            Object v1 = getValue();
            Object v2 = e.getValue();

            if (v1 == v2 || (v1 != null && v1.equals(v2)))
                return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return getKey().hashCode() ^ (getValue() == null || getValue() == getKey() ? 0 : getValue().hashCode());
    }

    @Override
    public String toString() {
        if (Debug.ENABLED) {
            if (this == REMOVED)
                return "REMOVED";

            if (isRemovalNoCheck())
                return getKey() + "=REMOVAL";

            return getKey() + "=" + getValue() + " (" + Integer.toHexString(System.identityHashCode(this)) + ")";
        }

        return getKey() + "=" + getValue();
    }
}