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

final class RefEqual {

    private final Object _object;

    public RefEqual(Object object) {
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
        return ((RefEqual) obj)._object == _object || obj == _object;
    }

    @Override
    public int hashCode() {
        if (_object instanceof TObject)
            return ((TObject) _object).hash();

        return System.identityHashCode(_object);
    }

    @Override
    public String toString() {
        return "RefEqual(" + _object + ")";
    }
}