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

final class LazyMapVersion extends TKeyedBase2 {

    public LazyMapVersion(TObject.Version shared) {
        super(shared);
    }

    @Override
    public boolean visitable(com.objectfabric.Visitor visitor, int mapIndex) {
        if (!super.visitable(visitor, mapIndex))
            return false;

        /*
         * If already acknowledged, version is going from server to client, so lazy data
         * should be ignored. TODO: unify with something for methods. TODO: decide for
         * each entry individually. If extension is connected to key, send message to
         * invalidate key on client. If extension is connected to value, update entry.
         */
        return mapIndex == com.objectfabric.Visitor.NULL_MAP_INDEX || mapIndex > visitor.getSnapshot().getAcknowledgedIndex();
    }

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getClassId() {
        // Only on shared version
        throw new RuntimeException();
    }
}
