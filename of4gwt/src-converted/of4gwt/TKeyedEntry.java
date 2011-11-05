/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package of4gwt;

import of4gwt.misc.SoftReference;
import java.util.Map;

import of4gwt.TObject.Version;
import of4gwt.misc.Debug;

final class TKeyedEntry<K, V> implements Map.Entry<K, V> {

    public static final Object REMOVAL = new Object() {

        @Override
        public String toString() {
            return "REMOVAL";
        }
    };

    public static final Object READ = new Object() {

        @Override
        public String toString() {
            return "READ";
        }
    };

    public static final TKeyedEntry REMOVED = new TKeyedEntry();

    private final K _key;

    private final int _hash;

    private final V _value;

    private final boolean _soft;

    private boolean _update;

    @SuppressWarnings("unchecked")
    public TKeyedEntry(K key, int hash, V value, boolean soft) {
        if (Debug.ENABLED) {
            Debug.assertion(key != null);

            if (soft)
                Debug.assertion(value != READ);
        }

        if (soft) {
            key = (K) new SoftReference<K>(key);
            value = value != REMOVAL ? (V) new SoftReference<V>(value) : (V) REMOVAL;
        }

        _key = key;
        _hash = hash;
        _value = value;
        _soft = soft;
    }

    private TKeyedEntry() {
        _key = null;
        _hash = 0;
        _value = null;
        _update = false;
        _soft = false;
    }

    @SuppressWarnings("unchecked")
    public K getKey() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        if (isSoft())
            return ((SoftReference<K>) _key).get();

        /*
         * Might be necessary to recreate object when merging a version of a TKeyed to its
         * shared version. Method 'equals' needs to be called on existing keys in the
         * shared version, which might have been GCed with the TKeyed before the merge.
         */
        if (_key instanceof Version)
            return (K) ((Version) _key).getOrRecreateTObject();

        return _key;
    }

    public K getKeyDirect() {
        if (Debug.ENABLED) {
            Debug.assertion(this != REMOVED);
            Debug.assertion(!isSoft());
        }

        return _key;
    }

    public int getHash() {
        return _hash;
    }

    public boolean isRemoval() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        boolean result = _value == REMOVAL;

        if (Debug.ENABLED && result)
            Debug.assertion(isUpdate());

        return result;
    }

    public boolean isRemovalNoCheck() {
        return _value == REMOVAL;
    }

    public boolean isRead() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        boolean result = _value == READ;

        if (Debug.ENABLED && result)
            Debug.assertion(!isRemoval());

        return result;
    }

    @SuppressWarnings("unchecked")
    public V getValue() {
        if (Debug.ENABLED)
            Debug.assertion(!isRemoval() && this != REMOVED);

        if (isSoft())
            return ((SoftReference<V>) _value).get();

        if (_value instanceof Version)
            return (V) ((Version) _value).getUserTObject_objectfabric();

        return _value;
    }

    public V getValueDirect() {
        if (Debug.ENABLED) {
            Debug.assertion(this != REMOVED);
            Debug.assertion(!isSoft());
        }

        return _value;
    }

    public V setValue(V value) {
        throw new UnsupportedOperationException();
    }

    public boolean isSoft() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        return _soft;
    }

    public boolean isGCed() {
        if (isSoft())
            return getKey() == null || getValue() == null;

        return false;
    }

    public boolean isUpdate() {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        return _update;
    }

    public void setIsUpdate(boolean value) {
        if (Debug.ENABLED)
            Debug.assertion(this != REMOVED);

        _update = value;
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

            String text = getKey() + "=" + getValue() + " (" + Integer.toHexString(System.identityHashCode(this));
            text += isUpdate() ? ", Update" : "";
            text += isSoft() ? ", Weak" : "";
            return text + ")";
        }

        return getKey() + "=" + getValue();
    }
}