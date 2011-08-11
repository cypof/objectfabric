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

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;

@SuppressWarnings("unchecked")
class TKeyedSharedVersion extends TKeyedBase1 {

    /*
     * Volatile since array can be resized during merge to a new one which might not be
     * visible to all thread.
     */
    private volatile TKeyedEntry[] _writes;

    private int _size;

    private TType[] _genericParameters;

    public TKeyedSharedVersion() {
        super(null);
    }

    public final TKeyedEntry[] getWrites() {
        return _writes;
    }

    public final void setWrites(TKeyedEntry[] value) {
        _writes = value;
    }

    public final int size() {
        return _size;
    }

    @Override
    public final TType[] getGenericParameters() {
        return _genericParameters;
    }

    public final void setGenericParameters(TType[] value) {
        _genericParameters = value;
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        if (Debug.ENABLED)
            Debug.assertion(flags == 0);

        TKeyedBase2 source = (TKeyedBase2) next;
        TKeyedEntry[] initialWrites = _writes;
        TKeyedEntry[] writes = initialWrites;

        if (source.getCleared()) {
            if (writes != null)
                for (int i = writes.length - 1; i >= 0; i--)
                    writes[i] = null;

            getReference().clearUserReferences();
            _entryCount = 0;
        }

        if (source.getEntries() != null) {
            for (int i = source.getEntries().length - 1; i >= 0; i--) {
                TKeyedEntry entry = source.getEntries()[i];

                if (entry != null && entry != TKeyedEntry.REMOVED) {
                    Object key = entry.getKeyDirect();
                    Object value = entry.getValueDirect();
                    boolean update = false;

                    if (key instanceof UserTObject) {
                        key = ((UserTObject) key).getSharedVersion_objectfabric();
                        update = true;
                    }

                    if (value instanceof UserTObject) {
                        value = ((UserTObject) value).getSharedVersion_objectfabric();
                        update = true;
                    }

                    if (update) {
                        boolean isUpdate = entry.isUpdate();
                        entry = new TKeyedEntry(key, entry.getHash(), value, false);
                        entry.setIsUpdate(isUpdate);
                    }

                    if (writes == null)
                        writes = new TKeyedEntry[source.getEntries().length];

                    TKeyedEntry previous = putEntryAndSkipOnException(writes, entry.getKey(), entry, false, false);

                    if (_entryCount > writes.length >> LOAD_BIT_SHIFT)
                        writes = rehash(writes);

                    onUpdatedSharedEntry(previous, entry);
                }
            }
        }

        if (writes != initialWrites)
            _writes = writes;

        if (source instanceof TKeyedVersion)
            _size = ((TKeyedVersion) source).size();

        if (Debug.ENABLED) {
            TKeyed object = (TKeyed) getReference().get();
            object.size(new Version[][] { new Version[] { this } });
        }

        return this;
    }

    private final void onUpdatedSharedEntry(TKeyedEntry previous, TKeyedEntry current) {
        TObject.Version previousKey = previous != null && previous.getKeyDirect() instanceof TObject.Version ? (TObject.Version) previous.getKeyDirect() : null;
        UserTObject currentKey = current.getKeyDirect() instanceof TObject.Version ? ((TObject.Version) current.getKeyDirect()).getUserTObject_objectfabric() : null;

        TObject.Version previousValue = null;
        UserTObject currentValue = null;

        if (previous != null) {
            if (previous.isRemoval())
                previousKey = null;
            else
                previousValue = previous.getValueDirect() instanceof TObject.Version ? (TObject.Version) previous.getValueDirect() : null;
        }

        if (current.isRemoval())
            currentKey = null;
        else
            currentValue = current.getValueDirect() instanceof TObject.Version ? ((TObject.Version) current.getValueDirect()).getUserTObject_objectfabric() : null;

        updateUserReference(previousKey, currentKey);
        updateUserReference(previousValue, currentValue);
    }

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public TKeyedRead createRead() {
        return new TKeyedRead(this);
    }

    // User references

    @Override
    protected void recreateUserReferences(Reference reference) {
        super.recreateUserReferences(reference);

        for (int i = 0; i < _writes.length; i++) {
            if (_writes[i] != null) {
                Object key = _writes[i].getKeyDirect();
                Object value = _writes[i].getValueDirect();

                if (key instanceof TObject.Version) {
                    TObject.Version shared = (TObject.Version) key;
                    addUserReference(reference, shared.getOrRecreateTObject());
                }

                if (value instanceof TObject.Version) {
                    TObject.Version shared = (TObject.Version) value;
                    addUserReference(reference, shared.getOrRecreateTObject());
                }
            }
        }
    }

    // Debug

    @Override
    public void checkInvariants_() {
        super.checkInvariants_();

        TKeyedEntry[] writes = _writes;

        if (writes != null)
            checkEntries(writes);
    }

    @Override
    public void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(_writes);
    }

    @Override
    public boolean hasWritesForDebug() {
        throw new AssertionError();
    }
}