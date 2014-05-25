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

import org.objectfabric.TObject.Version;

@SuppressWarnings("rawtypes")
class TKeyedBase2 extends TKeyedBase1 {

    TKeyedEntry[] _entries;

    private boolean _cleared;

    /*
     * TODO: load mutable objects here until read done,instead of putting them during
     * read, as their content might still be loading and hashCode would be wrong.
     */
    private Object[] _pendingPuts;

    final TKeyedEntry[] getEntries() {
        return _entries;
    }

    final void setEntries(TKeyedEntry[] value) {
        _entries = value;
    }

    final boolean getCleared() {
        return _cleared;
    }

    final void setCleared_(boolean value) {
        _cleared = value;
    }

    //

    final TKeyedEntry getWrite(Object key, int hash) {
        return getEntry(getEntries(), key, hash);
    }

    final void putEntry(TKeyedEntry entry, boolean keepRemovals, boolean check, boolean checkNoPrevious) {
        if (_entries == null)
            _entries = new TKeyedEntry[OpenMap.CAPACITY];

        // Might throw
        _entries = putEntry(_entries, entry, keepRemovals, checkNoPrevious);

        if (Debug.ENABLED)
            if (check)
                checkInvariants();
    }

    //

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TKeyedBase2 source = (TKeyedBase2) next;
        TKeyedBase2 merged = this;

        if (Debug.ENABLED) {
            source.checkInvariants();
            merged.checkInvariants();
        }

        if (source._cleared)
            merged._entries = source._entries;
        else if (source._entries != null) {
            if (merged._entries != null)
                merged = merge(target, merged, source, !threadPrivate);
            else {
                merged._entries = source._entries;

                if (hasRemovals(source._entries)) {
                    if (!threadPrivate) {
                        merged._entries = new TKeyedEntry[source._entries.length];
                        Platform.arraycopy(source._entries, 0, merged._entries, 0, source._entries.length);
                    }

                    for (int i = merged._entries.length - 1; i >= 0; i--) {
                        TKeyedEntry entry = merged._entries[i];

                        if (entry != null && entry != TKeyedEntry.REMOVED && entry.isRemoval())
                            merged._entries[i] = TKeyedEntry.REMOVED;
                    }
                }
            }
        }

        if (Debug.ENABLED)
            merged.checkInvariants();

        return merged;
    }

    private final boolean hasRemovals(TKeyedEntry[] entries) {
        for (int i = entries.length - 1; i >= 0; i--) {
            TKeyedEntry entry = entries[i];

            if (entry != null && entry != TKeyedEntry.REMOVED && entry.isRemoval())
                return true;
        }

        return false;
    }

    @Override
    void deepCopy(Version source_) {
        if (source_ instanceof TKeyedSharedVersion) {
            if (Debug.ENABLED)
                Debug.assertion(_entries == null);

            TKeyedSharedVersion source = (TKeyedSharedVersion) source_;

            if (source.getWrites() != null) {
                _entries = new TKeyedEntry[source.getWrites().length];
                Platform.arraycopy(source.getWrites(), 0, _entries, 0, _entries.length);
            }
        } else {
            TKeyedBase2 source = (TKeyedBase2) source_;

            if (_entries == null || source._cleared) {
                if (source._entries != null) {
                    _entries = new TKeyedEntry[source._entries.length];
                    Platform.arraycopy(source._entries, 0, _entries, 0, _entries.length);
                }
            } else if (source._entries != null)
                merge(this, this, source, false);
        }
    }

    @Override
    void clone(Version source_) {
        TKeyedBase2 source = (TKeyedBase2) source_;

        if (_entries == null || source._cleared)
            _entries = source._entries;
        else if (source._entries != null)
            merge(this, this, source, false);
    }

    final TKeyedBase2 merge(TObject.Version target, TKeyedBase2 merged, TKeyedBase2 source, boolean clone) {
        for (int i = source._entries.length - 1; i >= 0; i--) {
            TKeyedEntry entry = source._entries[i];

            if (entry != null && entry != TKeyedEntry.REMOVED) {
                boolean keepRemovals = !merged._cleared;
                TKeyedEntry[] update = merged.putEntryAndSkipOnException(merged._entries, entry, keepRemovals);

                if (update != merged._entries) {
                    if (this == target && clone)
                        merged = (TKeyedBase2) clone(this instanceof TKeyedRead);

                    merged._entries = update;
                }
            }
        }

        return merged;
    }

    //

    final boolean isConflict(TKeyedEntry write) {
        boolean conflict;

        try {
            conflict = getEntry(_entries, write.getKey(), write.getHash()) != null;
        } catch (Exception e) {
            Log.write(e);

            /*
             * Don't let a user exception from K.equals() go up into the system as it's
             * not tested for this so abort transaction or remove entry instead.
             */
            conflict = true;
        }

        return conflict;
    }

    // Debug

    @Override
    void checkInvariants_() {
        super.checkInvariants_();

        if (_entries != null)
            checkEntries(_entries);

        if (!(this instanceof TKeyedVersion))
            Debug.assertion(!_cleared);
    }

    @Override
    void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(_entries);
    }

    @Override
    boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_entries != null)
            for (int i = _entries.length - 1; i >= 0; i--)
                if (_entries[i] != null && _entries[i] != TKeyedEntry.REMOVED)
                    return true;

        return false;
    }
}