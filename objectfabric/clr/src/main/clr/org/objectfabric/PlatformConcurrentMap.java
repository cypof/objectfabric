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

@SuppressWarnings("unchecked")
 final class PlatformConcurrentMap<K, V> extends cli.ObjectFabric.ConcurrentHashMap {

    public void clear() {
        Clear();
    }

    public boolean containsKey(Object key) {
        return ContainsKey(key);
    }

    public Iterable<Map.Entry<K, V>> entrySet() {
        return EntrySet();
    }

    public V get(Object key) {
        return (V) Get(key);
    }

    public Iterable<K> keySet() {
        return KeySet();
    }

    public boolean isEmpty() {
        return IsEmpty();
    }

    public V put(K key, V value) {
        return (V) Put(key, value);
    }

    public V putIfAbsent(K key, V value) {
        return (V) PutIfAbsent(key, value);
    }

    public V remove(Object key) {
        return (V) Remove(key);
    }

    public boolean remove(Object key, Object expect) {
        return Remove(key, expect);
    }

    public boolean replace(K key, V expect, V update) {
        return Replace(key, expect, update);
    }

    public int size() {
        return Size();
    }

    public Iterable<V> values() {
        return Values();
    }
}
