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

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class TGeneratedFields32 extends TGeneratedFields {

    protected TGeneratedFields32(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    @Override
    public final Object getField(int index) {
        if (index < 0 || index >= getFieldCount())
            throw new IndexOutOfBoundsException();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TIndexed32Version version = getTIndexed32Version_objectfabric(inner, index);
        Object value = version != null ? getUserTObject_objectfabric(version.getAsObject(index)) : null;
        Transaction.endRead(outer, inner);
        return value;
    }

    /**
     * Return the field as it was when the current transaction started.
     */
    @Override
    public final Object getOldField(int index) {
        if (index < 0 || index >= getFieldCount())
            throw new IndexOutOfBoundsException();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TIndexed32Version version = findTIndexed32PublicVersion(inner, index);
        Object value = version != null ? getUserTObject_objectfabric(version.getAsObject(index)) : null;
        Transaction.endRead(outer, inner);
        return value;
    }

    @Override
    public final void setField(int index, Object value) {
        if (index < 0 || index >= getFieldCount())
            throw new IndexOutOfBoundsException();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TIndexed32Version version = (TIndexed32Version) getOrCreateVersion_objectfabric(inner);
        version.setAsObject(index, value);
        version.setBit(index);
        Transaction.endWrite(outer, inner);
    }

    @Override
    public final Iterator<Object> iterator() {
        return new IteratorImpl();
    }

    private final class IteratorImpl extends TIndexedIterator implements Iterator<Object> {

        public IteratorImpl() {
            super(getFieldCount());
        }

        public Object next() {
            if (_cursor < 0 || _cursor >= getFieldCount())
                throw new NoSuchElementException();

            Object next = getField(_cursor++);
            return next;
        }
    }

    protected static abstract class Version extends TIndexed32Version {

        public Version(TObject.Version shared, int length) {
            super(shared, length);
        }
    }
}
