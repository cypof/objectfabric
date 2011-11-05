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

package of4gwt.misc;

import of4gwt.Privileged;
import of4gwt.TObject;

public final class IdentityEqualityWrapper extends Privileged {

    private final Object _object;

    public IdentityEqualityWrapper(Object object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (object == null)
            throw new IllegalArgumentException();

        _object = object;
    }

    public Object getObject() {
        return _object;
    }

    @Override
    public boolean equals(Object obj) {
        return ((IdentityEqualityWrapper) obj)._object == _object || obj == _object;
    }

    @Override
    public int hashCode() {
        if (_object instanceof TObject)
            return getSharedHashCode((TObject) _object);

        return System.identityHashCode(_object);
    }

    @Override
    public String toString() {
        return "IdentityEqualityWrapper(" + _object.toString() + ")";
    }
}