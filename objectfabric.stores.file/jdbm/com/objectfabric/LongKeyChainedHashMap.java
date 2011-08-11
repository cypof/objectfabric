/*
 *  Primitive Collections for Java.
 *  Copyright (C) 2003  S�ren Bak
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.objectfabric;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class represents chained hash table based maps from long values to objects.
 * 
 * @see LongKeyOpenHashMap
 * @see java.util.Map
 * @author S&oslash;ren Bak
 * @version 1.2 21-08-2003 19:42
 * @since 1.0 ********* JDBM Project Note ************* This class was extracted from the
 *        pcj project (with permission) for use in jdbm only. Modifications to original
 *        were performed by Kevin Day to make it work outside of the pcj class structure.
 */
final class LongKeyChainedHashMap<E> {

    private static final int DEFAULT_CAPACITY = 11;

    private static final double LOAD_FACTOR = 0.75;

    /**
     * The size of this map.
     * 
     * @serial
     */
    private int size;

    /** The hash table backing up this map. Contains linked Entry<E> values. */
    private transient Entry<E>[] data; // TODO power of 2 & XOR

    /**
     * The next size at which to expand the data[].
     * 
     * @serial
     */
    private int expandAt;

    /** A collection view of the values of this map. */
    private transient Collection<E> values;

    public LongKeyChainedHashMap() {
        data = makeEntryArray(DEFAULT_CAPACITY);
        size = 0;
        expandAt = (int) Math.round(LOAD_FACTOR * DEFAULT_CAPACITY);
    }

    private static int hash(long v) {
        return (int) (v ^ (v >>> 32));
    }

    // ---------------------------------------------------------------
    // Hash table management
    // ---------------------------------------------------------------

    private void ensureCapacity(int elements) {
        if (elements >= expandAt) {
            int newCapacity = data.length << 1;

            if (newCapacity * LOAD_FACTOR < elements)
                newCapacity = (int) Math.round(elements / LOAD_FACTOR);

            newCapacity = Primes.nextPrime(newCapacity);
            expandAt = (int) Math.round(LOAD_FACTOR * newCapacity);

            Entry<E>[] newdata = makeEntryArray(newCapacity);

            // re-hash
            for (int i = 0; i < data.length; i++) {
                Entry<E> e = data[i];
                while (e != null) {
                    int index = Math.abs(hash(e.key)) % newdata.length;
                    Entry<E> next = e.next;
                    e.next = newdata[index];
                    newdata[index] = e;
                    e = next;
                }
            }

            data = newdata;
        }
    }

    @SuppressWarnings("unchecked")
    private Entry<E>[] makeEntryArray(int newcapacity) {
        return new Entry[newcapacity];
    }

    private Entry<E> removeList(Entry<E> list, Entry<E> e) {
        if (list == e) {
            list = e.next;
            e.next = null;
            return list;
        }
        Entry<E> listStart = list;
        while (list.next != e)
            list = list.next;
        list.next = e.next;
        e.next = null;
        return listStart;
    }

    private Entry<E> searchList(Entry<E> list, long key) {
        while (list != null) {
            if (list.key == key)
                return list;
            list = list.next;
        }
        return null;
    }

    private Entry<E> getEntry(long key) {
        int index = Math.abs(hash(key)) % data.length;
        return searchList(data[index], key);
    }

    // ---------------------------------------------------------------
    // Operations not supported by abstract implementation
    // ---------------------------------------------------------------

    public E put(long key, E value) {
        E result;
        int index = Math.abs(hash(key)) % data.length;
        Entry<E> e = searchList(data[index], key);
        if (e == null) {
            result = null;
            e = new Entry<E>(key, value);
            e.next = data[index];
            data[index] = e;
            // Capacity is increased after insertion in order to
            // avoid recalculation of index
            ensureCapacity(size + 1);
            size++;
        } else {
            result = e.value;
            e.value = value;
        }
        return result;
    }

    public Collection<E> values() {
        if (values == null)
            values = new ValueCollection();
        return values;
    }

    private static class Entry<E> {

        long key;

        E value;

        Entry<E> next;

        Entry(long key, E value) {
            this.key = key;
            this.value = value;
        }

        public long getKey() {
            return key;
        }

        public E getValue() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object obj) {
            if (!(obj instanceof Entry))
                return false;
            Entry<E> e = (Entry<E>) obj;
            Object eval = e.getValue();
            if (eval == null)
                return e.getKey() == key && value == null;
            return e.getKey() == key && e.getValue().equals(value);
        }
    }

    private class ValueCollection extends AbstractCollection<E> {

        @Override
        public void clear() {
            LongKeyChainedHashMap.this.clear();
        }

        @Override
        public boolean contains(Object v) {
            return containsValue(v);
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {

                Entry<E> currEntry = null;

                int nextList = nextList(0);

                Entry<E> nextEntry = nextList == -1 ? null : data[nextList];

                int nextList(int index) {
                    while (index < data.length && data[index] == null)
                        index++;
                    return index < data.length ? index : -1;
                }

                public boolean hasNext() {
                    return nextEntry != null;
                }

                public E next() {
                    if (nextEntry == null)
                        Exceptions.endOfIterator();
                    currEntry = nextEntry;

                    // Find next
                    nextEntry = nextEntry.next;
                    if (nextEntry == null) {
                        nextList = nextList(nextList + 1);
                        if (nextList != -1)
                            nextEntry = data[nextList];
                    }
                    return currEntry.value;
                }

                public void remove() {
                    if (currEntry == null)
                        Exceptions.noElementToRemove();
                    LongKeyChainedHashMap.this.remove(currEntry.getKey());
                    currEntry = null;
                }
            };
        }

        @Override
        public int size() {
            return size;
        }
    }

    public void clear() {
        java.util.Arrays.fill(data, null);
        size = 0;
    }

    public boolean containsKey(long key) {
        Entry<E> e = getEntry(key);
        return e != null;
    }

    public boolean containsValue(Object value) {
        if (value == null) {
            for (int i = 0; i < data.length; i++) {
                Entry<E> e = data[i];
                while (e != null) {
                    if (e.value == null)
                        return true;
                    e = e.next;
                }
            }
        } else {
            for (int i = 0; i < data.length; i++) {
                Entry<E> e = data[i];
                while (e != null) {
                    if (value.equals(e.value))
                        return true;
                    e = e.next;
                }
            }
        }
        return false;
    }

    public E get(long key) {
        int index = Math.abs(hash(key)) % data.length;
        Entry<E> e = searchList(data[index], key);
        return e != null ? e.value : null;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public E remove(long key) {
        int index = Math.abs(hash(key)) % data.length;
        Entry<E> e = searchList(data[index], key);
        E value;
        if (e != null) {
            // This can be improved to one iteration
            data[index] = removeList(data[index], e);
            value = e.value;
            size--;
        } else
            value = null;
        return value;
    }

    public int size() {
        return size;
    }
}