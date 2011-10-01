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
import of4gwt.misc.Log;

final class TKeyedRead extends TKeyedBase2 {

    private boolean _fullyRead;

    public TKeyedRead(TObject.Version shared) {
        super(shared);
    }

    public final boolean getFullyRead() {
        return _fullyRead;
    }

    public final void setFullyRead(boolean value) {
        _fullyRead = value;
    }

    private final boolean isRead(Object key, int hash) {
        TKeyedEntry entry = getEntry(_entries, key, hash);

        if (Debug.ENABLED)
            if (entry != null)
                Debug.assertion(entry.isRead());

        return entry != null;
    }

    @Override
    public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
        for (int i = start; i < stop; i++) {
            boolean skip = false;

            if (map.getSource() != null) {
                Connection.Version c = snapshot.getVersionMaps()[i].getSource() != null ? snapshot.getVersionMaps()[i].getSource().Connection : null;
                skip = map.getSource().Connection == c;
            }

            if (!skip) {
                TObject.Version write = TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], getUnion());

                if (write != null) {
                    TKeyedVersion version = (TKeyedVersion) write;

                    if (!validAgainst(version))
                        return false;
                }
            }
        }

        return true;
    }

    private final boolean validAgainst(TKeyedVersion version) {
        if (_fullyRead)
            if (version.getEntries() != null || version.getCleared())
                return false;

        if (Debug.ENABLED)
            Debug.assertion(_entries != null && version.getEntries() != null);

        if (version.getCleared())
            return false;

        for (int i = version.getEntries().length - 1; i >= 0; i--) {
            TKeyedEntry entry = version.getEntries()[i];

            if (entry != null) {
                boolean conflict;

                try {
                    conflict = isRead(entry.getKey(), entry.getHash());
                } catch (Exception e) {
                    Log.write(e);

                    /*
                     * Don't let a user exception from K.equals() go up into the system as
                     * it's not tested for this so abort transaction instead.
                     */
                    conflict = true;
                }

                if (conflict)
                    return false;
            }
        }

        return true;
    }

    //

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TKeyedRead source = (TKeyedRead) next;
        TKeyedRead merged = (TKeyedRead) super.merge(target, next, flags);
        merged._fullyRead |= source._fullyRead;
        return merged;
    }

    @Override
    public void visit(of4gwt.Visitor visitor) {
        visitor.visit(this);
    }
}