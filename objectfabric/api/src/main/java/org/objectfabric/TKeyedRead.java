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
final class TKeyedRead extends TKeyedBase2 {

    private boolean _fullyRead;

    public final boolean getFullyRead() {
        return _fullyRead;
    }

    public final void setFullyRead(boolean value) {
        _fullyRead = value;
    }

    @Override
    public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        for (int i = start; i < stop; i++) {
            TObject.Version write = TransactionBase.getVersion(snapshot.writes()[i], object());

            if (write != null) {
                TKeyedBase2 version = (TKeyedBase2) write;

                if (!validAgainst(version))
                    return false;
            }
        }

        return true;
    }

    private final boolean validAgainst(TKeyedBase2 version) {
        if (_fullyRead)
            if (version.getEntries() != null || version.getCleared())
                return false;

        if (Debug.ENABLED)
            Debug.assertion(_entries != null && version.getEntries() != null);

        if (version.getCleared())
            return false;

        for (int i = version.getEntries().length - 1; i >= 0; i--) {
            TKeyedEntry entry = version.getEntries()[i];

            if (entry != null && isConflict(entry))
                return false;
        }

        return true;
    }

    //

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TKeyedRead source = (TKeyedRead) next;
        TKeyedRead merged = (TKeyedRead) super.merge(target, next, threadPrivate);
        merged._fullyRead |= source._fullyRead;
        return merged;
    }

    @Override
    void deepCopy(Version source_) {
        TKeyedRead source = (TKeyedRead) source_;
        super.deepCopy(source);
        _fullyRead |= source._fullyRead;
    }

    @Override
    void clone(Version source_) {
        TKeyedRead source = (TKeyedRead) source_;
        super.clone(source_);
        _fullyRead |= source._fullyRead;
    }

    @Override
    void visit(org.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }
}