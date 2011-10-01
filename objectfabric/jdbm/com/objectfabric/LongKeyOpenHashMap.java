/*
 *  Primitive Collections for Java.
 *  Copyright (C) 2002, 2003  S�ren Bak
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

/**
 * This class represents open addressing hash table based maps from long values to
 * objects.
 * 
 * @see LongKeyChainedHashMap
 * @see java.util.Map
 * @author S&oslash;ren Bak
 * @version 1.3 21-08-2003 19:45
 * @since 1.0 ********* JDBM Project Note ************* This class was extracted from the
 *        pcj project (with permission) for use in jdbm only. Modifications to original
 *        were performed by Kevin Day to make it work outside of the pcj class structure.
 */
final public class LongKeyOpenHashMap<E> {

    private static final int DEFAULT_CAPACITY = 11;

    private static final double LOAD_FACTOR = 0.75;

    /**
     * The size of this map.
     * 
     * @serial
     */
    private int size;

    /**
     * The keys of this map. Contains key values directly. Due to the use of a secondary
     * hash function, the length of this array must be a prime.
     */
    private long[] keys;

    /**
     * The values of this map. Contains values directly. Due to the use of a secondary
     * hash function, the length of this array must be a prime.
     */
    private Object[] values;

    /** The states of each cell in the keys[] and values[]. */
    private transient byte[] states;

    private static final byte EMPTY = 0;

    private static final byte OCCUPIED = 1;

    private static final byte REMOVED = 2;

    /** The number of entries in use (removed or occupied). */
    private transient int used;

    /**
     * The next size at which to expand the data[].
     * 
     * @serial
     */
    private int expandAt;

    /**
     * Creates a new hash map with a specified capacity, a relative growth factor of 1.0,
     * and a load factor of 75%.
     * 
     * @param capacity
     *            the initial capacity of the map.
     * @throws IllegalArgumentException
     *             if <tt>capacity</tt> is negative.
     */
    public LongKeyOpenHashMap(int capacity) {
        capacity = Primes.nextPrime(capacity);
        keys = new long[capacity];
        values = new Object[capacity];
        this.states = new byte[capacity];
        size = 0;
        expandAt = (int) Math.round(LOAD_FACTOR * DEFAULT_CAPACITY);
        this.used = 0;
    }

    private static int hash(long v) {
        return (int) (v ^ (v >>> 32));
    }

    // ---------------------------------------------------------------
    // Hash table management
    // ---------------------------------------------------------------

    private void ensureCapacity(int elements) {
        if (elements >= expandAt) {
            int newcapacity = keys.length << 1;

            if (newcapacity * LOAD_FACTOR < elements)
                newcapacity = (int) Math.round((elements / LOAD_FACTOR));

            newcapacity = Primes.nextPrime(newcapacity);
            expandAt = (int) Math.round(LOAD_FACTOR * newcapacity);

            long[] newkeys = new long[newcapacity];
            Object[] newvalues = new Object[newcapacity];
            byte[] newstates = new byte[newcapacity];

            used = 0;
            // re-hash
            for (int i = 0; i < keys.length; i++) {
                if (states[i] == OCCUPIED) {
                    used++;
                    long k = keys[i];
                    Object v = values[i];
                    // first hash
                    int h = Math.abs(hash(k));
                    int n = h % newcapacity;
                    if (newstates[n] == OCCUPIED) {
                        // second hash
                        int c = 1 + (h % (newcapacity - 2));
                        for (;;) {
                            n -= c;
                            if (n < 0)
                                n += newcapacity;
                            if (newstates[n] == EMPTY)
                                break;
                        }
                    }
                    newstates[n] = OCCUPIED;
                    newvalues[n] = v;
                    newkeys[n] = k;
                }
            }

            keys = newkeys;
            values = newvalues;
            states = newstates;
        }
    }

    // ---------------------------------------------------------------
    // Operations not supported by abstract implementation
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public E put(long key, E value) {

        // first hash
        int h = Math.abs(hash(key));
        int i = h % keys.length;
        if (states[i] == OCCUPIED) {
            if (keys[i] == key) {
                Object oldValue = values[i];
                values[i] = value;
                return (E) oldValue;
            }
            // second hash
            int c = 1 + (h % (keys.length - 2));
            for (;;) {
                i -= c;
                if (i < 0)
                    i += keys.length;
                // Empty entries are re-used
                if (states[i] == EMPTY || states[i] == REMOVED)
                    break;
                if (states[i] == OCCUPIED && keys[i] == key) {
                    Object oldValue = values[i];
                    values[i] = value;
                    return (E) oldValue;
                }
            }
        }

        if (states[i] == EMPTY)
            used++;
        states[i] = OCCUPIED;
        keys[i] = key;
        values[i] = value;
        size++;
        ensureCapacity(used);
        return null;
    }

    @SuppressWarnings("unchecked")
    public E get(long key) {
        int h = Math.abs(hash(key));
        int i = h % keys.length;
        if (states[i] != EMPTY) {
            if (states[i] == OCCUPIED && keys[i] == key)
                return (E) values[i];
            // second hash

            int c = 1 + (h % (keys.length - 2));
            for (;;) {
                i -= c;
                if (i < 0)
                    i += keys.length;
                if (states[i] == EMPTY)
                    return null;
                if (states[i] == OCCUPIED && keys[i] == key)
                    return (E) values[i];
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public E remove(long key) {
        int h = Math.abs(hash(key));
        int i = h % keys.length;
        if (states[i] != EMPTY) {
            if (states[i] == OCCUPIED && keys[i] == key) {
                Object oldValue = values[i];
                values[i] = null; // GC
                states[i] = REMOVED;
                size--;
                return (E) oldValue;
            }
            // second hash
            int c = 1 + (h % (keys.length - 2));
            for (;;) {
                i -= c;
                if (i < 0)
                    i += keys.length;
                if (states[i] == EMPTY) {
                    return null;
                }
                if (states[i] == OCCUPIED && keys[i] == key) {
                    Object oldValue = values[i];
                    values[i] = null; // GC
                    states[i] = REMOVED;
                    size--;
                    return (E) oldValue;
                }
            }
        }
        return null;
    }

    public int size() {
        return size;
    }
}