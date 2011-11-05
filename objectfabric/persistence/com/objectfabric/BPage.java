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

package com.objectfabric;

import com.objectfabric.BTree.Walker;
import com.objectfabric.TObject.Record;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Utils;

/**
 * Adapted from JDBM BPage.
 */
final class BPage {

    final BTree _tree;

    /**
     * This BPage's record ID in the PageManager.
     */
    long _recid;

    private final byte[][] _keys = new byte[BTree.PAGE_SIZE][];

    private final long[] _values = new long[BTree.PAGE_SIZE];

    /**
     * Index of first used item at the page
     */
    private int _first;

    /**
     * Root page overflow constructor
     */
    BPage(BTree btree, BPage root, BPage overflow) {
        _tree = btree;
        _first = BTree.PAGE_SIZE - 2;

        _keys[BTree.PAGE_SIZE - 2] = overflow.getLargestKey();
        _keys[BTree.PAGE_SIZE - 1] = root.getLargestKey();

        _values[BTree.PAGE_SIZE - 2] = overflow._recid;
        _values[BTree.PAGE_SIZE - 1] = root._recid;

        _recid = _tree.getRecordManager().insert(getBytes());
    }

    /**
     * Root page (first insert) constructor.
     */
    BPage(BTree btree, byte[] key, long value) {
        if (Debug.ENABLED)
            if (btree.isFixedKeyLength())
                Debug.assertion(key.length == BTree.FIXED_KEY_LENGTH);

        _tree = btree;
        _first = BTree.PAGE_SIZE - 2;

        _keys[BTree.PAGE_SIZE - 2] = key;
        _values[BTree.PAGE_SIZE - 2] = value;

        _recid = _tree.getRecordManager().insert(getBytes());
    }

    /**
     * Overflow page constructor. Creates an empty BPage.
     */
    private BPage(BTree btree) {
        _tree = btree;

        // page will initially be half-full
        _first = BTree.PAGE_SIZE / 2;
    }

    BPage(BTree tree, long recid) {
        _tree = tree;
        _recid = recid;
    }

    /**
     * Get largest key under this BPage. Null is considered to be the greatest possible
     * key.
     */
    private byte[] getLargestKey() {
        return _keys[BTree.PAGE_SIZE - 1];
    }

    /**
     * Return true if BPage is empty.
     */
    boolean isEmpty() {
        return (_first == _values.length - 1);
    }

    /**
     * Return true if BPage is full.
     */
    boolean isFull() {
        return (_first == 0);
    }

    long get(int height, byte[] key) {
        if (Debug.ENABLED)
            if (_tree.isFixedKeyLength())
                Debug.assertion(key.length == BTree.FIXED_KEY_LENGTH);

        height -= 1;

        if (height == 0) {
            return getValue(key);
        }

        int index = findChildren(key);
        BPage child = childBPage(index);
        return child.get(height, key);
    }

    void walk(int height, Walker walker) {
        height -= 1;

        if (height == 0) {
            for (int i = _first; i < _keys.length - 1; i++)
                walker.onEntry(_keys[i], _values[i]);

            if (_keys[_keys.length - 1] != null)
                walker.onEntry(_keys[_keys.length - 1], _values[_values.length - 1]);
        } else {
            for (int i = _first; i < _values.length; i++) {
                BPage childBPage = childBPage(i);
                childBPage.walk(height, walker);
            }
        }
    }

    /**
     * Deletes this BPage and all children pages from the record manager
     */
    void delete(int height, boolean deleteLeafs) {
        height -= 1;

        if (height == 0) {
            if (deleteLeafs) {
                for (int i = _first; i < _values.length - 1; i++)
                    _tree.getRecordManager().delete(_values[i]);

                if (_keys[_keys.length - 1] != null)
                    _tree.getRecordManager().delete(_values[_values.length - 1]);
            }
        } else {
            for (int i = _first; i < _values.length; i++) {
                BPage childBPage = childBPage(i);
                childBPage.delete(height, deleteLeafs);
            }
        }

        _tree.getRecordManager().delete(_recid);
    }

    /**
     * @return Insertion result containing existing value OR a BPage if the key was
     *         inserted and provoked a BPage overflow.
     */
    InsertResult insert(int height, byte[] key, long value) {
        if (Debug.ENABLED)
            if (_tree.isFixedKeyLength())
                Debug.assertion(key.length == BTree.FIXED_KEY_LENGTH);

        InsertResult result;
        long entry;
        int index = findChildren(key);
        height -= 1;

        if (height == 0) {
            result = new InsertResult();

            // inserting on a leaf BPage
            entry = value;

            if (compare(key, _keys[index]) == 0) {
                // key already exists

                result._existing = _values[index];
                _values[index] = value;
                update();

                // return the existing key
                return result;
            }
        } else {
            // non-leaf BPage
            BPage child = childBPage(index);
            result = child.insert(height, key, value);

            if (result._existing != Record.NOT_STORED) {
                // return existing key, if any.
                return result;
            }

            if (result._overflow == null) {
                // no overflow means we're done with insertion
                return result;
            }

            // there was an overflow, we need to insert the overflow page on
            // this BPage

            key = result._overflow.getLargestKey();
            entry = result._overflow._recid;

            // update child's largest key
            _keys[index] = child.getLargestKey();

            // clean result so we can reuse it
            result._overflow = null;
        }

        // if we get here, we need to insert a new entry on the BPage
        // before _children[ index ]
        if (!isFull()) {
            insertEntry(this, index - 1, key, entry);
            update();
            return result;
        }

        // page is full, we must divide the page
        int half = BTree.PAGE_SIZE >> 1;
        BPage newPage = new BPage(_tree);

        if (index < half) {
            // move lower-half of entries to overflow BPage, including new entry
            copyEntries(this, 0, newPage, half, index);
            setEntry(newPage, half + index, key, entry);
            copyEntries(this, index, newPage, half + index + 1, half - index - 1);
        } else {
            // move lower-half of entries to overflow BPage, new entry stays on
            // this BPage
            copyEntries(this, 0, newPage, half, half);
            copyEntries(this, half, this, half - 1, index - half);
            setEntry(this, index - 1, key, entry);
        }

        _first = half - 1;

        // nullify lower half of entries
        for (int i = 0; i < _first; i++)
            setEntry(this, i, null, Record.NOT_STORED);

        update();
        newPage._recid = _tree.getRecordManager().insert(newPage.getBytes());

        result._overflow = newPage;
        return result;
    }

    /**
     * Remove the entry associated with the given key.
     * 
     * @param height
     *            Height of the current BPage (zero is leaf page)
     * @param key
     *            Removal key
     * @return Remove result object
     */
    RemoveResult remove(int height, byte[] key) {
        if (Debug.ENABLED)
            if (_tree.isFixedKeyLength())
                Debug.assertion(key.length == BTree.FIXED_KEY_LENGTH);

        RemoveResult result;

        int half = BTree.PAGE_SIZE / 2;
        int index = findChildren(key);
        height -= 1;

        if (height == 0) {
            // remove leaf entry
            if (compare(_keys[index], key) != 0)
                throw new IllegalArgumentException("Key not found: " + key);

            result = new RemoveResult();
            result._value = _values[index];
            removeEntry(this, index);

            // update this BPage
            update();
        } else {
            // recurse into BTree to remove entry on a children page
            BPage child = childBPage(index);
            result = child.remove(height, key);

            // update children
            _keys[index] = child.getLargestKey();
            update();

            if (result._underflow) {
                // underflow occurred
                if (child._first != half + 1)
                    throw new IllegalStateException("Error during underflow [1]");

                if (index < _values.length - 1) {
                    // exists greater brother page
                    BPage brother = childBPage(index + 1);
                    int bfirst = brother._first;

                    if (bfirst < half) {
                        // steal entries from "brother" page
                        int steal = (half - bfirst + 1) / 2;
                        brother._first += steal;
                        child._first -= steal;

                        copyEntries(child, half + 1, child, half + 1 - steal, half - 1);
                        copyEntries(brother, bfirst, child, 2 * half - steal, steal);

                        for (int i = bfirst; i < bfirst + steal; i++)
                            setEntry(brother, i, null, Record.NOT_STORED);

                        // update child's largest key
                        _keys[index] = child.getLargestKey();

                        // no change in previous/next BPage

                        // update BPages
                        update();
                        brother.update();
                        child.update();
                    } else {
                        // move all entries from page "child" to "brother"

                        if (brother._first != half)
                            throw new IllegalStateException("Error during underflow [2]");

                        brother._first = 1;
                        copyEntries(child, half + 1, brother, 1, half - 1);
                        brother.update();

                        // remove "child" from current BPage
                        copyEntries(this, _first, this, _first + 1, index - _first);
                        setEntry(this, _first, null, Record.NOT_STORED);
                        _first += 1;
                        update();

                        // delete "child" BPage
                        _tree.getRecordManager().delete(child._recid);
                    }
                } else {
                    // page "brother" is before "child"
                    BPage brother = childBPage(index - 1);
                    int bfirst = brother._first;

                    if (bfirst < half) {
                        // steal entries from "brother" page
                        int steal = (half - bfirst + 1) / 2;
                        brother._first += steal;
                        child._first -= steal;

                        copyEntries(brother, 2 * half - steal, child, half + 1 - steal, steal);
                        copyEntries(brother, bfirst, brother, bfirst + steal, 2 * half - bfirst - steal);

                        for (int i = bfirst; i < bfirst + steal; i++)
                            setEntry(brother, i, null, Record.NOT_STORED);

                        // update brother's largest key
                        _keys[index - 1] = brother.getLargestKey();

                        // no change in previous/next BPage

                        // update BPages
                        update();
                        brother.update();
                        child.update();
                    } else {
                        // move all entries from page "brother" to "child"

                        if (brother._first != half)
                            throw new IllegalStateException("Error during underflow [3]");

                        child._first = 1;
                        copyEntries(brother, half, child, 1, half);
                        child.update();

                        // remove "brother" from current BPage
                        copyEntries(this, _first, this, _first + 1, index - 1 - _first);
                        setEntry(this, _first, null, Record.NOT_STORED);
                        _first += 1;
                        update();

                        // delete "brother" BPage
                        _tree.getRecordManager().delete(brother._recid);
                    }
                }
            }
        }

        // underflow if page is more than half-empty
        result._underflow = _first > half;
        return result;
    }

    /**
     * Find the first children node with a key equal or greater than the given key.
     * 
     * @return index of first children with equal or greater key.
     */
    private int findChildren(byte[] key) {
        int left = _first;
        int right = BTree.PAGE_SIZE - 1;

        // binary search
        while (left < right) {
            int middle = (left + right) / 2;

            if (compare(_keys[middle], key) < 0)
                left = middle + 1;
            else
                right = middle;
        }

        return right;
    }

    private long getValue(byte[] key) {
        int left = _first;
        int right = BTree.PAGE_SIZE - 1;

        // binary search
        for (;;) {
            int middle = (left + right) / 2;
            int result = compare(_keys[middle], key);

            if (result == 0)
                return _values[middle];

            if (left == right)
                return Record.NOT_STORED;

            if (result < 0)
                left = middle + 1;
            else
                right = middle;
        }
    }

    /**
     * Insert entry at given position.
     */
    private static void insertEntry(BPage page, int index, byte[] key, long value) {
        byte[][] keys = page._keys;
        long[] values = page._values;
        int start = page._first;
        int count = index - page._first + 1;

        // shift entries to the left
        System.arraycopy(keys, start, keys, start - 1, count);
        System.arraycopy(values, start, values, start - 1, count);
        page._first -= 1;
        keys[index] = key;
        values[index] = value;
    }

    /**
     * Remove entry at given position.
     */
    private static void removeEntry(BPage page, int index) {
        byte[][] keys = page._keys;
        long[] values = page._values;
        int start = page._first;
        int count = index - page._first;

        System.arraycopy(keys, start, keys, start + 1, count);
        keys[start] = null;
        System.arraycopy(values, start, values, start + 1, count);
        values[start] = Record.NOT_STORED;
        page._first++;
    }

    /**
     * Set the entry at the given index.
     */
    private static void setEntry(BPage page, int index, byte[] key, long value) {
        page._keys[index] = key;
        page._values[index] = value;
    }

    /**
     * Copy entries between two BPages
     */
    private static void copyEntries(BPage source, int indexSource, BPage dest, int indexDest, int count) {
        System.arraycopy(source._keys, indexSource, dest._keys, indexDest, count);
        System.arraycopy(source._values, indexSource, dest._values, indexDest, count);
    }

    /**
     * Return the child BPage at given index.
     */
    BPage childBPage(int index) {
        byte[] data = _tree.getRecordManager().fetch(_values[index]);
        BPage page = new BPage(_tree, _values[index]);
        page.setBytes(data);
        return page;
    }

    private static final int compare(byte[] value1, byte[] value2) {
        if (value1 == null)
            return 1;

        if (value2 == null)
            return -1;

        int len = Math.min(value1.length, value2.length);

        // TODO: simplify?
        for (int i = 0; i < len; i++) {
            if (value1[i] >= 0) {
                if (value2[i] >= 0) {
                    // both positive
                    if (value1[i] < value2[i])
                        return -1;

                    if (value1[i] > value2[i])
                        return 1;
                } else {
                    // value2 is negative => greater (because MSB is 1)
                    return -1;
                }
            } else {
                if (value2[i] >= 0) {
                    // value1 is negative => greater (because MSB is 1)
                    return 1;
                }

                // both negative
                if (value1[i] < value2[i])
                    return -1;

                if (value1[i] > value2[i])
                    return 1;
            }
        }

        if (value1.length == value2.length)
            return 0;

        if (value1.length < value2.length)
            return -1;

        return 1;
    }

    //

    private void update() {
        _tree.getRecordManager().update(_recid, getBytes());
    }

    private static final int LAST_KEY = 1 << 7;

    private static final int LAST_VALUE = 1 << 6;

    private static final int MIN_BIT = 1 << 6;

    /*
     * TODO make BPage a wrapper around the array, or inject a reader in the manager,
     * instead of serializing / deserializing.
     */
    private byte[] getBytes() {
        if (Debug.ENABLED)
            Debug.assertion(_keys.length < MIN_BIT);

        int length = 1;
        int info = _first;
        int maxKey = BTree.PAGE_SIZE - 1;
        int maxValue = BTree.PAGE_SIZE - 1;

        if (_tree.isFixedKeyLength())
            length += (BTree.PAGE_SIZE - 1 - _first) * BTree.FIXED_KEY_LENGTH;
        else
            for (int i = _first; i < _keys.length - 1; i++)
                length += 4 + _keys[i].length;

        if (_keys[_keys.length - 1] != null) {
            if (_tree.isFixedKeyLength())
                length += BTree.FIXED_KEY_LENGTH;
            else
                length += 4 + _keys[_keys.length - 1].length;

            maxKey++;
            info |= LAST_KEY;
        }

        length += (BTree.PAGE_SIZE - 1 - _first) * 8;

        if (_values[_values.length - 1] != Record.NOT_STORED) {
            length += 8;
            maxValue++;
            info |= LAST_VALUE;
        }

        byte[] data = new byte[length];
        int offset = 0;
        data[offset++] = (byte) info;

        for (int i = _first; i < maxKey; i++) {
            if (!_tree.isFixedKeyLength()) {
                Utils.writeInt(data, offset, _keys[i].length);
                offset += 4;
            }

            PlatformAdapter.arraycopy(_keys[i], 0, data, offset, _keys[i].length);
            offset += _keys[i].length;
        }

        for (int i = _first; i < maxValue; i++) {
            Utils.writeLong(data, offset, _values[i]);
            offset += 8;
        }

        if (Debug.ENABLED)
            Debug.assertion(offset == data.length);

        return data;
    }

    void setBytes(byte[] data) {
        int offset = 0;
        int info = data[offset++] & 0xff;
        int maxKey = BTree.PAGE_SIZE - 1;
        int maxValue = BTree.PAGE_SIZE - 1;

        _first = info & ~(LAST_KEY | LAST_VALUE);

        if ((info & LAST_KEY) != 0)
            maxKey++;

        if ((info & LAST_VALUE) != 0)
            maxValue++;

        for (int i = _first; i < maxKey; i++) {
            int length = BTree.FIXED_KEY_LENGTH;

            if (!_tree.isFixedKeyLength()) {
                length = Utils.readInt(data, offset);
                offset += 4;
            }

            _keys[i] = new byte[length];
            PlatformAdapter.arraycopy(data, offset, _keys[i], 0, length);
            offset += length;
        }

        for (int i = _first; i < maxValue; i++) {
            _values[i] = Utils.readLong(data, offset);
            offset += 8;
        }

        if (Debug.ENABLED)
            Debug.assertion(offset == data.length);
    }

    // Move to fields on BTree

    /**
     * STATIC INNER CLASS Result from insert() method call
     */
    static final class InsertResult {

        /**
         * Overflow page.
         */
        BPage _overflow;

        /**
         * Existing value for the insertion key.
         */
        long _existing;
    }

    /**
     * STATIC INNER CLASS Result from remove() method call
     */
    static final class RemoveResult {

        /**
         * Set to true if underlying pages underflowed
         */
        boolean _underflow;

        /**
         * Removed entry value
         */
        long _value;
    }
}