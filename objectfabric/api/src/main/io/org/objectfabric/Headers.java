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

/**
 * Common representation of connection headers. If a transport does not supports headers
 * natively, e.g. sockets, or response headers in case of WebSocket, OF will serialize
 * them internally. Keeps ordering and allows multiple values per key as required by HTTP.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Headers {

    private final java.util.ArrayList<Entry> _entries = new java.util.ArrayList<Entry>();

    /**
     * Returns the header value with the specified header name. If there are more than one
     * header value for the specified header name, the first value is returned.
     */
    public String get(String name) {
        for (int i = 0; i < _entries.size(); i++)
            if (_entries.get(i).getKey().equals(name))
                return _entries.get(i).getValue();

        return null;
    }

    /**
     * Returns the header values with the specified header name.
     */
    public java.util.List<String> getMultiple(String name) {
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();

        for (int i = 0; i < _entries.size(); i++)
            if (_entries.get(i).getKey().equals(name))
                list.add(_entries.get(i).getValue());

        return list;
    }

    /**
     * Returns all header names and values.
     */
    public java.util.List<Map.Entry<String, String>> getAsList() {
        return (java.util.List) _entries;
    }

    /**
     * Returns true if and only if there is a header with the specified header name.
     */
    public boolean contains(String name) {
        for (int i = 0; i < _entries.size(); i++)
            if (_entries.get(i).getKey().equals(name))
                return true;

        return false;
    }

    /**
     * Returns the set of all header names.
     */
    public java.util.Set<String> getNames() {
        PlatformSet<String> set = new PlatformSet<String>();

        for (int i = 0; i < _entries.size(); i++)
            set.add(_entries.get(i).getKey());

        return set;
    }

    /**
     * Adds a new header with the specified name and value.
     */
    public void add(String name, String value) {
        _entries.add(new Entry(name, value));
    }

    /**
     * Sets a new header with the specified name and value. If there is an existing header
     * with the same name, the existing header is removed.
     */
    public void set(String name, String value) {
        removeHeader(name);
        add(name, value);
    }

    /**
     * Sets a new header with the specified name and values. If there is an existing
     * header with the same name, the existing header is removed.
     */
    public void set(String name, Iterable<String> values) {
        removeHeader(name);

        for (String value : values)
            add(name, value);
    }

    /**
     * Removes the header with the specified name.
     */
    public void removeHeader(String name) {
        for (int i = _entries.size() - 1; i >= 0; i--)
            if (_entries.get(i).getKey().equals(name))
                _entries.remove(i);
    }

    /**
     * Removes all headers.
     */
    public void clearHeaders() {
        _entries.clear();
    }

    final String[] asStrings() {
        java.util.List<Map.Entry<String, String>> list = getAsList();
        String[] strings = new String[list.size() * 2];

        for (int i = 0; i < strings.length;) {
            Map.Entry<String, String> entry = list.get(i / 2);
            strings[i++] = entry.getKey();
            strings[i++] = entry.getValue();
        }

        return strings;
    }

    private static final class Entry implements Map.Entry<String, String> {

        private final String _key, _value;

        Entry(String key, String value) {
            if (key == null || value == null)
                throw new NullPointerException();

            _key = key;
            _value = value;
        }

        @Override
        public java.lang.String getKey() {
            return _key;
        }

        @Override
        public java.lang.String getValue() {
            return _value;
        }

        @Override
        public java.lang.String setValue(java.lang.String value) {
            throw new UnsupportedOperationException();
        }
    }
}
