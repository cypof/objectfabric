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

abstract class TIndexed32Version extends TIndexed32Read {

    private final int _length;

    public TIndexed32Version(TObject.Version shared, int length) {
        super(shared, length);

        _length = length;
    }

    public final int length() {
        return _length;
    }

    //

    public Object getAsObject(int index) {
        throw new IllegalStateException();
    }

    public void setAsObject(int index, Object value) {
        throw new IllegalStateException();
    }

    //

    @Override
    public boolean isImmutable() {
        return getReadOnlys() == (1 << _length) - 1;
    }

    @Override
    public void visit(of4gwt.Visitor visitor) {
        visitor.visit(this);
    }

    //

    @Override
    public TObject.Version createRead() {
        return new TIndexed32Read(this, length());
    }

    // User references

    @Override
    protected void recreateUserReferences(Reference reference) {
        super.recreateUserReferences(reference);

        for (int i = 0; i < length(); i++) {
            Object object = getAsObject(i);

            if (object instanceof UserTObject)
                addUserReference(reference, (UserTObject) object);
        }
    }

    //

    public void writeWrite(Writer writer, int index) {
        throw new IllegalStateException();
    }

    public void readWrite(Reader reader, int index) {
        throw new IllegalStateException();
    }
}