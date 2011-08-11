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

package of4gwt;

import of4gwt.misc.Debug;
import of4gwt.misc.List;

class TKeyedBase2 extends TKeyedBase1 {

    protected TKeyedEntry[] _entries;

    private boolean _cleared;

    public TKeyedBase2(TObject.Version shared) {
        super(shared);
    }

    public final TKeyedEntry[] getEntries() {
        return _entries;
    }

    public final void setEntries(TKeyedEntry[] value) {
        _entries = value;
    }

    public final boolean getCleared() {
        return _cleared;
    }

    protected final void setCleared_(boolean value) {
        _cleared = value;
    }

    //

    public final TKeyedEntry getWrite(Object key, int hash) {
        return getEntry(getEntries(), key, hash);
    }

    public final TKeyedEntry putEntry(Object key, TKeyedEntry entry, boolean keepRemovals, boolean check) {
        if (_entries == null)
            _entries = new TKeyedEntry[DEFAULT_INITIAL_CAPACITY];

        // Might throw
        TKeyedEntry previous = putEntry(_entries, key, entry, keepRemovals, false);

        if (_entryCount > _entries.length >> LOAD_BIT_SHIFT)
            _entries = rehash(_entries);

        if (Debug.ENABLED)
            if (check)
                checkInvariants();

        return previous;
    }

    //

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TKeyedBase2 source = (TKeyedBase2) next;
        TKeyedBase2 merged = this;

        if (Debug.ENABLED) {
            if ((flags & MERGE_FLAG_CLONE) == 0) {
                source.checkInvariants();
                merged.checkInvariants();
            }
        }

        if (merged._entries == null || source._cleared) {
            merged._entries = source._entries;
            merged._entryCount = source._entryCount;
        } else if (source._entries != null)
            merged = merge(target, merged, source, flags);

        if (Debug.ENABLED)
            if ((flags & MERGE_FLAG_CLONE) == 0)
                merged.checkInvariants();

        return merged;
    }

    protected final TKeyedBase2 merge(TObject.Version target, TKeyedBase2 merged, TKeyedBase2 source, int flags) {
        for (int i = source._entries.length - 1; i >= 0; i--) {
            TKeyedEntry entry = source._entries[i];

            if (entry != null && entry != TKeyedEntry.REMOVED) {
                merged.putEntryAndSkipOnException(merged._entries, entry.getKey(), entry, !(this instanceof TKeyedVersion), false);

                if (merged._entryCount > merged._entries.length >> LOAD_BIT_SHIFT)
                    merged = merged.rehash(target, flags);
            }
        }

        return merged;
    }

    private final TKeyedBase2 rehash(TObject.Version target, int flags) {
        if (Debug.ENABLED)
            Debug.assertion((flags & MERGE_FLAG_CLONE) == 0);

        TKeyedBase2 result = this;

        if (result == target && (flags & (MERGE_FLAG_PRIVATE | MERGE_FLAG_BY_COPY)) == 0)
            result = (TKeyedBase2) cloneThis((flags & MERGE_FLAG_READS) != 0);

        result._entries = rehash(result._entries);
        return result;
    }

    // Debug

    @Override
    public void checkInvariants_() {
        super.checkInvariants_();

        if (_entries != null)
            checkEntries(_entries);

        if (!(this instanceof TKeyedVersion))
            Debug.assertion(!_cleared);
    }

    @Override
    public void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(_entries);
    }

    @Override
    public boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (_entries != null)
            for (int i = _entries.length - 1; i >= 0; i--)
                if (_entries[i] != null)
                    return true;

        return false;
    }
}