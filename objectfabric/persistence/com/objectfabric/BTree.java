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

import com.objectfabric.TObject.Record;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Utils;

/**
 * Adapted from JDBM BTree.
 */
final class BTree {

    public static interface Walker {

        void onEntry(byte[] key, long value);
    }

    static final int PAGE_SIZE = 16;

    static final int FIXED_KEY_LENGTH = PlatformAdapter.UID_BYTES_COUNT;

    private final RecordManager _recman;

    private final long _id;

    private final boolean _fixedKeyLength;

    /**
     * Height of the B+Tree. This is the number of BPages you have to traverse to get to a
     * leaf BPage, starting from the root.
     */
    private int _height;

    /*
     * TODO keep one page per level (C.f. comment in serialization). Needs to manage roll
     * backs.
     */
    private long _root;

    private boolean _cleared;

    public BTree(RecordManager recman, boolean fixedKeyLength) {
        _recman = recman;
        _fixedKeyLength = fixedKeyLength;
        _id = recman.insert(write());
    }

    private BTree(RecordManager recman, long recid, boolean fixedKeyLength) {
        _recman = recman;
        _id = recid;
        _fixedKeyLength = fixedKeyLength;
    }

    public RecordManager getRecordManager() {
        return _recman;
    }

    public long getId() {
        return _id;
    }

    public boolean isFixedKeyLength() {
        return _fixedKeyLength;
    }

    /**
     * Height of the B+Tree. This is the number of BPages you have to traverse to get to a
     * leaf BPage, starting from the root.
     */
    public int getHeight() {
        return _height;
    }

    public static BTree load(RecordManager recman, long recid, boolean fixedKeyLength) {
        if (Stats.ENABLED)
            Stats.getInstance().BTreeLoads.incrementAndGet();

        byte[] data = recman.fetch(recid);
        BTree tree = null;

        if (data != null) {
            if (Debug.ENABLED)
                Debug.assertion(data.length == SERIALIZATION_LENGTH);

            tree = new BTree(recman, recid, fixedKeyLength);
            tree.read(data);
        }

        return tree;
    }

    public static BTree loadReadOnly(RecordManager recman, byte[] data, boolean fixedKeyLength) {
        if (Stats.ENABLED)
            Stats.getInstance().BTreeLoads.incrementAndGet();

        BTree tree = new BTree(recman, Record.NOT_STORED, fixedKeyLength);
        tree.read(data);
        return tree;
    }

    private static final int SERIALIZATION_LENGTH = 14;

    private void read(byte[] data) {
        if (Debug.ENABLED)
            Debug.assertion(data.length == BTree.SERIALIZATION_LENGTH);

        // Ignore flags
        _height = Utils.readInt(data, 1);
        _root = Utils.readLong(data, 5);
        _cleared = data[13] != 0;
    }

    private byte[] write() {
        byte[] data = new byte[1 + 4 + 8 + 1];
        data[0] = (byte) (PlatformAdapter.SUPPORTED_SERIALIZATION_FLAGS | CompileTimeSettings.SERIALIZATION_VERSION);
        Utils.writeInt(data, 1, _height);
        Utils.writeLong(data, 5, _root);
        data[13] = _cleared ? (byte) 1 : 0;
        return data;
    }

    public long put(byte[] key, long value) {
        if (Stats.ENABLED)
            Stats.getInstance().BTreePuts.incrementAndGet();

        if (key == null)
            throw new IllegalArgumentException("Argument 'key' is null");

        if (_id == Record.NOT_STORED)
            throw new IllegalStateException("Tree is read-lony");

        BPage rootPage = getRoot();

        if (rootPage == null) {
            // BTree is currently empty, create a new root BPage
            if (Debug.PERSISTENCE_LOG)
                Log.write("BTree.insert() new root BPage");

            rootPage = new BPage(this, key, value);
            _root = rootPage._recid;
            _height = 1;
            _recman.update(_id, write());
            return Record.NOT_STORED;
        } else {
            BPage.InsertResult insert = rootPage.insert(_height, key, value);
            boolean dirty = false;

            if (insert._overflow != null) {
                // current root page overflowed, we replace with a new root page
                if (Debug.PERSISTENCE_LOG)
                    Log.write("BTree.insert() replace root BPage due to overflow");

                rootPage = new BPage(this, rootPage, insert._overflow);
                _root = rootPage._recid;
                _height += 1;
                dirty = true;
            }

            if (insert._existing == Record.NOT_STORED)
                dirty = true;

            if (dirty)
                _recman.update(_id, write());

            // insert might have returned an existing value
            return insert._existing;
        }
    }

    /**
     * @return Value associated with the key, or Record.NOT_STORED.
     */
    public long remove(byte[] key) {
        if (Stats.ENABLED)
            Stats.getInstance().BTreeRemoves.incrementAndGet();

        if (key == null)
            throw new IllegalArgumentException("Argument 'key' is null");

        if (_id == Record.NOT_STORED)
            throw new IllegalStateException("Tree is read-lony");

        BPage rootPage = getRoot();

        if (rootPage == null)
            return Record.NOT_STORED;

        boolean dirty = false;
        BPage.RemoveResult remove = rootPage.remove(_height, key);

        if (remove._underflow && rootPage.isEmpty()) {
            _height -= 1;
            dirty = true;

            _recman.delete(_root);

            if (_height == 0)
                _root = Record.NOT_STORED;
            else
                _root = rootPage.childBPage(PAGE_SIZE - 1)._recid;
        }

        if (remove._value != Record.NOT_STORED)
            dirty = true;

        if (dirty)
            _recman.update(_id, write());

        return remove._value;
    }

    public long fetch(byte[] key) {
        if (Stats.ENABLED)
            Stats.getInstance().BTreeFetches.incrementAndGet();

        if (key == null)
            throw new IllegalArgumentException("Argument 'key' is null");

        BPage rootPage = getRoot();

        if (rootPage == null)
            return Record.NOT_STORED;

        return rootPage.get(_height, key);
    }

    /**
     * This tree has been cleared. This is used when trees represent object versions as
     * the tree now overrides previous versions.
     */
    public boolean getCleared() {
        return _cleared;
    }

    /**
     * Deletes all BPages in this BTree.
     */
    public void clear(boolean deleteLeafs) {
        if (_id == Record.NOT_STORED)
            throw new IllegalStateException("Tree is read-lony");

        BPage rootPage = getRoot();

        if (rootPage != null)
            rootPage.delete(_height, deleteLeafs);

        _root = Record.NOT_STORED;
        _height = 0;
        _cleared = true;
        _recman.update(_id, write());
    }

    /**
     * Clears then deletes the tree from the record manager
     */
    public void delete(boolean deleteLeafs) {
        if (_id == Record.NOT_STORED)
            throw new IllegalStateException("Tree is read-lony");

        BPage rootPage = getRoot();

        if (rootPage != null)
            rootPage.delete(_height, deleteLeafs);

        _recman.delete(_id);
    }

    public void walk(Walker walker) {
        BPage rootPage = getRoot();

        if (rootPage != null)
            rootPage.walk(_height, walker);
    }

    /**
     * Return the root BPage, or null if it doesn't exist.
     */
    private BPage getRoot() {
        if (_root == Record.NOT_STORED)
            return null;

        BPage page = new BPage(this, _root);
        byte[] data = _recman.fetch(_root);
        page.setBytes(data);
        return page;
    }
}
