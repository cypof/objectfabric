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
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getClassId() {
        // Only on shared version
        throw new RuntimeException();
    }
}
