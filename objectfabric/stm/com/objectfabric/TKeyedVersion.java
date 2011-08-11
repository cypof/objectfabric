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

import java.util.HashSet;

import com.objectfabric.TKeyed.KeyedIterator;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;

final class TKeyedVersion extends TKeyedBase2 {

    private int _size = -1;

    private int _sizeDelta = 0;

    private boolean _verifySizeDeltaOnCommit;

    public TKeyedVersion(TObject.Version shared) {
        super(shared);
    }

    public final boolean sizeValid() {
        return _size >= 0;
    }

    public final int size() {
        if (Debug.ENABLED)
            Debug.assertion(sizeValid());

        return _size;
    }

    public final int sizeDelta() {
        return _sizeDelta;
    }

    public final boolean getVerifySizeDeltaOnCommit() {
        return _verifySizeDeltaOnCommit;
    }

    public final void setVerifySizeDeltaOnCommit() {
        _verifySizeDeltaOnCommit = true;
    }

    public final void setCleared(boolean value) {
        setCleared_(value);
    }

    public final void clearCollection() {
        if (_entries != null)
            for (int i = _entries.length - 1; i >= 0; i--)
                _entries[i] = null;

        _entryCount = 0;
        _sizeDelta = 0;
        setCleared(true);
        _verifySizeDeltaOnCommit = false;

        if (Debug.ENABLED)
            Debug.assertion(!sizeValid());
    }

    //

    public final void onPutEntry(TKeyedEntry entry, boolean existed) {
        if (!entry.isRemoval() && !existed)
            _sizeDelta++;
        else if (entry.isRemoval() && existed)
            _sizeDelta--;

        if (Debug.ENABLED)
            checkInvariants();
    }

    //

    @Override
    public final boolean onPublishing(Snapshot newSnapshot, int mapIndex) {
        if (Debug.ENABLED) {
            boolean retry = Helper.getInstance().getRetryCount() > 0;
            Debug.assertion(!sizeValid() || retry);
        }

        updateFromSnapshot(newSnapshot, mapIndex, false);
        return true;
    }

    @Override
    public Version onPastChanged(Snapshot newSnapshot, int mapIndex) {
        if (Debug.ENABLED)
            Debug.assertion(sizeValid());

        TKeyedVersion result = (TKeyedVersion) cloneThis(false);
        result.setVerifySizeDeltaOnCommit();
        result.updateFromSnapshot(newSnapshot, mapIndex, true);
        return result;
    }

    @Override
    public void onDeserialized(Snapshot transactionSnapshot) {
        _sizeDelta = updateEntriesAndGetDelta(transactionSnapshot, transactionSnapshot.getWrites().length - 1, false);
    }

    private void updateFromSnapshot(Snapshot snapshot, int mapIndex, boolean clone) {
        int sizeDelta = _sizeDelta;

        if (_verifySizeDeltaOnCommit)
            sizeDelta = updateEntriesAndGetDelta(snapshot, mapIndex, clone);

        int size = 0;

        if (!getCleared())
            size = getPreviousSize(snapshot, mapIndex, getUnion());

        _size = size + sizeDelta;

        if (Debug.ENABLED) {
            checkInvariants();

            TKeyed object = (TKeyed) getShared().getReference().get();
            KeyedIterator iterator = object.createIterator(snapshot.getWrites(), mapIndex);
            HashSet test = new HashSet();

            while (iterator.hasNext()) {
                if (Debug.ENABLED)
                    Helper.getInstance().disableEqualsOrHashCheck();

                @SuppressWarnings("unchecked")
                boolean added = test.add(iterator.nextEntry().getKey());

                if (Debug.ENABLED)
                    Helper.getInstance().enableEqualsOrHashCheck();

                Debug.assertion(added);
            }

            Debug.assertion(_size == test.size());
        }
    }

    @SuppressWarnings("unchecked")
    private int updateEntriesAndGetDelta(Snapshot snapshot, int mapIndex, boolean clone) {
        TKeyedEntry[] entries = _entries;

        if (Debug.ENABLED)
            Debug.assertion(!getCleared());

        int sizeDelta = 0;

        /*
         * Recompute delta by checking if writes add or remove elements.
         */
        if (_entries != null) {
            for (int i = _entries.length - 1; i >= 0; i--) {
                TKeyedEntry entry = _entries[i];

                if (entry != null && entry != TKeyedEntry.REMOVED) {
                    TKeyedEntry previous = TKeyed.getEntry(snapshot.getWrites(), mapIndex, entry.getKey(), entry.getHash(), getUnion());
                    boolean existed = previous != null && !previous.isRemoval();

                    if (entry.isUpdate() != existed) {
                        if (clone) {
                            if (_entries == entries) {
                                TKeyedEntry[] temp = new TKeyedEntry[_entries.length];
                                PlatformAdapter.arraycopy(_entries, 0, temp, 0, _entries.length);
                                _entries = temp;
                            }

                            if (Debug.ENABLED)
                                Debug.assertion(!entry.isSoft());

                            entry = new TKeyedEntry(entry.getKeyDirect(), entry.getHash(), entry.getValueDirect(), false);
                            _entries[i] = entry;
                        }

                        entry.setIsUpdate(existed);
                    }

                    if (!entry.isRemoval() && !existed)
                        sizeDelta++;
                    else if (entry.isRemoval() && existed)
                        sizeDelta--;
                    else if (entry.isRemoval() && !existed) {
                        if (clone && _entries == entries) {
                            TKeyedEntry[] temp = new TKeyedEntry[_entries.length];
                            PlatformAdapter.arraycopy(_entries, 0, temp, 0, _entries.length);
                            _entries = temp;
                        }

                        _entries[i] = TKeyedEntry.REMOVED;
                        _entryCount--;
                    }
                }
            }
        }

        return sizeDelta;
    }

    private static int getPreviousSize(Snapshot snapshot, int mapIndex, Object shared) {
        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TObject.Version version = TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], shared);

            if (version != null)
                return ((TKeyedVersion) version).size();
        }

        return ((TKeyedSharedVersion) shared).size();
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TKeyedVersion source = (TKeyedVersion) next;
        TKeyedVersion merged = (TKeyedVersion) super.merge(target, next, flags);

        if ((flags & MERGE_FLAG_CLONE) != 0) {
            merged.setCleared(source.getCleared());
            merged._sizeDelta = source._sizeDelta;
            merged._size = source._size;
            merged._verifySizeDeltaOnCommit = source._verifySizeDeltaOnCommit;
        } else {
            if (source.getCleared()) {
                if (Debug.ENABLED)
                    Debug.assertion(!source._verifySizeDeltaOnCommit);

                merged.setCleared(true);
                merged._sizeDelta = 0;
                merged._verifySizeDeltaOnCommit = false;
            }

            if ((flags & MERGE_FLAG_PRIVATE) != 0) {
                merged._sizeDelta += source._sizeDelta;

                if (Debug.ENABLED)
                    Debug.assertion(!merged.sizeValid() && !source.sizeValid());
            } else {
                if (Debug.ENABLED)
                    Debug.assertion(merged.sizeValid() && source.sizeValid());

                merged._size = source._size;
            }
        }

        return merged;
    }

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    // Debug

    @Override
    public Version createVersion() {
        // Only on shared version
        throw new RuntimeException();
    }

    static void assertUpdates(Version[][] snapshot, int mapIndex) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Version[] versions = snapshot[mapIndex];

        for (Version baseVersion : versions) {
            if (baseVersion instanceof TKeyedVersion) {
                TKeyedVersion version = (TKeyedVersion) baseVersion;

                if (version._entries != null) {
                    for (TKeyedEntry entry : version._entries) {
                        if (entry != null && entry != TKeyedEntry.REMOVED) {
                            if (version.getCleared())
                                Debug.assertion(!entry.isUpdate());
                            else {
                                TKeyedEntry previous = TKeyed.getEntry(snapshot, mapIndex, entry.getKey(), entry.getHash(), version.getShared());
                                boolean existed = previous != null && !previous.isRemoval();

                                if (entry != previous)
                                    Debug.assertion(entry.isUpdate() == existed);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(getCleared());
    }

    @Override
    public boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (getCleared())
            return true;

        return super.hasWritesForDebug();
    }
}