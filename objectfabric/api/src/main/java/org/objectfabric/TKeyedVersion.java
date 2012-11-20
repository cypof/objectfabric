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

import java.util.HashSet;

import org.objectfabric.TKeyed.KeyedIterator;
import org.objectfabric.TObject.Version;

@SuppressWarnings("rawtypes")
final class TKeyedVersion extends TKeyedBase2 {

    private int _size = -1;

    private int _sizeDelta = 0;

    private boolean _verifySizeDeltaOnCommit;

    final boolean sizeValid() {
        return _size >= 0;
    }

    final int size() {
        if (Debug.ENABLED)
            Debug.assertion(sizeValid());

        return _size;
    }

    final int sizeDelta() {
        return _sizeDelta;
    }

    final boolean getVerifySizeDeltaOnCommit() {
        return _verifySizeDeltaOnCommit;
    }

    final void setVerifySizeDeltaOnCommit() {
        _verifySizeDeltaOnCommit = true;
    }

    final void setCleared(boolean value) {
        setCleared_(value);
    }

    final void clearCollection() {
        if (_entries != null)
            for (int i = _entries.length - 1; i >= 0; i--)
                _entries[i] = null;

        _sizeDelta = 0;
        setCleared(true);
        _verifySizeDeltaOnCommit = false;

        if (Debug.ENABLED)
            Debug.assertion(!sizeValid());
    }

    //

    final void onPutEntry(TKeyedEntry entry, boolean existed) {
        if (!entry.isRemoval() && !existed)
            _sizeDelta++;
        else if (entry.isRemoval() && existed)
            _sizeDelta--;

        if (Debug.ENABLED)
            checkInvariants();
    }

    //

    @Override
    final void onPublishing(Snapshot newSnapshot, int mapIndex) {
        if (Debug.ENABLED) {
            boolean retry = Helper.instance().getRetryCount() > 0;
            Debug.assertion(!sizeValid() || retry);
        }

        updateFromSnapshot(newSnapshot, mapIndex);
    }

    @Override
    void onDeserialized(Snapshot transactionSnapshot) {
        _sizeDelta = updateEntriesAndGetDelta(transactionSnapshot, transactionSnapshot.writes().length - 1);
    }

    private void updateFromSnapshot(Snapshot snapshot, int mapIndex) {
        int sizeDelta = _sizeDelta;

        if (_verifySizeDeltaOnCommit)
            sizeDelta = updateEntriesAndGetDelta(snapshot, mapIndex);

        int size = 0;

        if (!getCleared())
            size = getPreviousSize(snapshot, mapIndex, object());

        _size = size + sizeDelta;

        if (Debug.ENABLED) {
            checkInvariants();

            TKeyed object = (TKeyed) object();
            KeyedIterator iterator = object.createIterator(snapshot.writes(), mapIndex);
            HashSet test = new HashSet();

            while (iterator.hasNext()) {
                if (Debug.ENABLED)
                    Helper.instance().disableEqualsOrHashCheck();

                @SuppressWarnings("unchecked")
                boolean added = test.add(iterator.nextEntry().getKey());

                if (Debug.ENABLED)
                    Helper.instance().enableEqualsOrHashCheck();

                Debug.assertion(added);
            }

            Debug.assertion(_size == test.size());
        }
    }

    private int updateEntriesAndGetDelta(Snapshot snapshot, int mapIndex) {
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
                    TKeyedEntry previous = TKeyed.getEntry(snapshot.writes(), object(), mapIndex, entry.getKey(), entry.getHash());
                    boolean existed = previous != null && !previous.isRemoval();

                    if (!entry.isRemoval() && !existed)
                        sizeDelta++;
                    else if (entry.isRemoval() && existed)
                        sizeDelta--;
                    else if (entry.isRemoval() && !existed)
                        _entries[i] = TKeyedEntry.REMOVED;
                }
            }
        }

        return sizeDelta;
    }

    private static int getPreviousSize(Snapshot snapshot, int mapIndex, TObject object) {
        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TObject.Version version = TransactionBase.getVersion(snapshot.writes()[i], object);

            if (version != null)
                return ((TKeyedVersion) version).size();
        }

        return ((TKeyedSharedVersion) object.shared_()).size();
    }

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TKeyedVersion source = (TKeyedVersion) next;
        TKeyedVersion merged = (TKeyedVersion) super.merge(target, next, threadPrivate);

        if (source.getCleared()) {
            if (Debug.ENABLED)
                Debug.assertion(!source._verifySizeDeltaOnCommit);

            merged.setCleared(true);
            merged._sizeDelta = 0;
            merged._verifySizeDeltaOnCommit = false;
        }

        if (threadPrivate) {
            merged._sizeDelta += source._sizeDelta;

            if (Debug.ENABLED)
                Debug.assertion(!merged.sizeValid() && !source.sizeValid());
        } else {
            if (Debug.ENABLED)
                Debug.assertion(merged.sizeValid() && source.sizeValid());

            merged._size = source._size;
        }

        return merged;
    }

    @Override
    void deepCopy(Version source_) {
        super.deepCopy(source_);

        if (source_ instanceof TKeyedSharedVersion) {
            TKeyedSharedVersion source = (TKeyedSharedVersion) source_;
            _size = source.size();
        } else {
            TKeyedVersion source = (TKeyedVersion) source_;

            if (source.getCleared()) {
                if (Debug.ENABLED)
                    Debug.assertion(!source._verifySizeDeltaOnCommit);

                setCleared(true);
                _sizeDelta = 0;
                _verifySizeDeltaOnCommit = false;
            }

            _size = source._size;
        }
    }

    @Override
    void clone(Version source_) {
        super.clone(source_);

        TKeyedVersion source = (TKeyedVersion) source_;
        setCleared(source.getCleared());
        _size = source._size;
    }

    @Override
    void visit(org.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    final boolean mask(Version version) {
        TKeyedBase2 keyed = (TKeyedBase2) version;
        boolean empty = true;

        if (getCleared())
            keyed.setEntries(null);
        else {
            if (keyed.getEntries() != null) {
                for (int i = keyed.getEntries().length - 1; i >= 0; i--) {
                    TKeyedEntry entry = keyed.getEntries()[i];

                    if (entry != null && entry != TKeyedEntry.REMOVED) {
                        if (isConflict(entry))
                            keyed.getEntries()[i] = TKeyedEntry.REMOVED;
                        else
                            empty = false;
                    }
                }
            }
        }

        return empty;
    }

    // Debug

    @Override
    void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(getCleared());
    }

    @Override
    boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (getCleared())
            return true;

        return super.hasWritesForDebug();
    }
}