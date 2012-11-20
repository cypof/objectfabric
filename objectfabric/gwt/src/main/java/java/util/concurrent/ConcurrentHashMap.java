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

package java.util.concurrent;

import java.util.HashMap;

public class ConcurrentHashMap<K, V> extends HashMap<K, V> {

    public V putIfAbsent(K key, V value) {
        if (!containsKey(key))
            return put(key, value);

        return get(key);
    }

    public boolean replace(K key, V oldValue, V newValue) {
        if (containsKey(key) && get(key).equals(oldValue)) {
            put(key, newValue);
            return true;
        }

        return false;
    }

    public boolean remove(Object key, Object value) {
        if (containsKey(key) && get(key).equals(value)) {
            remove(key);
            return true;
        }

        return false;
    }
}