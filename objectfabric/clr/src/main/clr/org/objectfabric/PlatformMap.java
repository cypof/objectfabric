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

import cli.ObjectFabric.CLRMap;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
@SuppressWarnings("unchecked")
public final class PlatformMap<K, V> extends CLRMap {

    public void clear() {
        Clear();
    }

    public boolean containsKey(K key) {
        return ContainsKey(key);
    }

    public Iterable<Map.Entry<K, V>> entrySet() {
        return EntrySet();
    }

    public V get(K key) {
        return (V) Get(key);
    }

    public Iterable<K> keySet() {
        return Keys();
    }

    public V remove(K key) {
        V previous = get(key);
        Remove(key);
        return previous;
    }

    public V put(K key, V value) {
        V previous = get(key);
        Put(key, value);
        return previous;
    }

    public int size() {
        return get_Count();
    }

    public Iterable<V> values() {
        return Values();
    }
}
