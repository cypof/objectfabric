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
final class TKeyedSharedVersion extends TKeyedBase1 {

    /*
     * Volatile since array can be resized during merge to a new one which might not be
     * visible to all thread.
     */
    private volatile TKeyedEntry[] _writes;

    private int _size;

    final TKeyedEntry[] getWrites() {
        return _writes;
    }

    final void setWrites(TKeyedEntry[] value) {
        _writes = value;
    }

    final int size() {
        return _size;
    }

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TKeyedBase2 source = (TKeyedBase2) next;
        TKeyedEntry[] initialWrites = _writes;
        TKeyedEntry[] writes = initialWrites;

        if (source.getCleared())
            if (writes != null)
                for (int i = writes.length - 1; i >= 0; i--)
                    writes[i] = null;

        if (source.getEntries() != null) {
            for (int i = source.getEntries().length - 1; i >= 0; i--) {
                TKeyedEntry entry = source.getEntries()[i];

                if (entry != null && entry != TKeyedEntry.REMOVED) {
                    if (writes == null)
                        writes = new TKeyedEntry[source.getEntries().length];

                    writes = putEntryAndSkipOnException(writes, entry, false);
                }
            }
        }

        if (writes != initialWrites)
            _writes = writes;

        if (threadPrivate) {
            _size = 0;

            for (int i = _writes.length - 1; i >= 0; i--)
                if (_writes[i] != null && _writes[i] != TKeyedEntry.REMOVED)
                    _size++;
        } else
            _size = ((TKeyedVersion) source).size();

        if (Debug.ENABLED)
            ((TKeyed) object()).size(new Version[][] { new Version[] { this } });

        return this;
    }

    @Override
    void deepCopy(Version source) {
        super.deepCopy(source);

        if (Debug.ENABLED)
            Debug.fail();
    }

    @Override
    void visit(Visitor visitor) {
        visitor.visit(this);
    }

    // Debug

    @Override
    void checkInvariants_() {
        super.checkInvariants_();

        TKeyedEntry[] writes = _writes;

        if (writes != null)
            checkEntries(writes);

        if (writes != null)
            for (int i = writes.length - 1; i >= 0; i--)
                if (writes[i] != null && writes[i] != TKeyedEntry.REMOVED)
                    Debug.assertion(!writes[i].isRemoval());
    }

    @Override
    void getContentForDebug(List<Object> list) {
        super.getContentForDebug(list);

        list.add(_writes);
    }

    @Override
    boolean hasWritesForDebug() {
        throw new AssertionError();
    }
}