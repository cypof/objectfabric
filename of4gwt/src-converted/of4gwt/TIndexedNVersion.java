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


import of4gwt.Reader;
import of4gwt.Writer;
import of4gwt.misc.Bits;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.Utils;

abstract class TIndexedNVersion extends TIndexedNRead {

    private final int _length;

    public TIndexedNVersion(TObject.Version shared, int length) {
        super(shared);

        /*
         * Shared array must never be reindexed, for other threads visibility, as it is
         * not volatile: allocate necessary length from beginning.
         */
        if (shared == null) {
            int entryCount = Bits.arrayLength(length);
            setBits(PlatformAdapter.createBitsArray(Utils.nextPowerOf2(entryCount)));
        }

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
    public void visit(of4gwt.Visitor visitor) {
        visitor.visit(this);
    }

    //

    @Override
    public TObject.Version createRead() {
        return new TIndexedNRead(this);
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

    // For TArray

    protected final void mergeObjects(Object[] merged, Bits.Entry writes, Object[] source) {
        for (int i = Bits.BITS_PER_UNIT - 1; i >= 0; i--)
            if (Bits.get(writes.Value, i))
                merged[i] = mergeObject(merged[i], source != null ? source[i] : null);
    }
}