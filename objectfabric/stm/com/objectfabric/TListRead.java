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

import com.objectfabric.Connection;

final class TListRead extends TObject.Version {

    public TListRead(TObject.Version shared) {
        super(shared);
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
                TObject.Version write = TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], getShared());

                if (write != null)
                    return false;
            }
        }

        return true;
    }

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }
}