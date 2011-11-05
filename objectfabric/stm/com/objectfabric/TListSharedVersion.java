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

import com.objectfabric.misc.Bits;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Utils;

final class TListSharedVersion extends TObject.Version {

    /*
     * Volatile since array can be resized during merge to a new one which might not be
     * visible to all thread.
     */
    private volatile Object[] _array;

    private int _size;

    private TType[] _genericParameters;

    public TListSharedVersion() {
        super(null);
    }

    public Object[] getArray() {
        return _array;
    }

    public int size() {
        return _size;
    }

    @Override
    public final TType[] getGenericParameters() {
        return _genericParameters;
    }

    public final void setGenericParameters(TType[] value) {
        _genericParameters = value;
    }

    public Object get(int index) {
        Object[] array = _array;

        if (array != null)
            return array[index];

        return null;
    }

    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        TListVersion source = (TListVersion) next;
        Object[] initialArray = _array;
        Object[] array = initialArray;

        if (source.getCleared()) {
            if (Debug.ENABLED) {
                Debug.assertion(source.getRemovals() == null);
                Debug.assertion(source.getRemovalsCount() == 0);
            }

            if (array != null) {
                if (array.length < source.size())
                    array = new Object[Utils.nextPowerOf2(source.size())];
                else
                    for (int i = array.length - 1; i >= 0; i--)
                        array[i] = null; // for GC
            }

            getReference().clearUserReferences();
            _size = 0;
        } else {
            if (array != null && array.length < source.size()) {
                Object[] temp = new Object[Utils.nextPowerOf2(source.size())];
                PlatformAdapter.arraycopy(array, 0, temp, 0, _size);
                array = temp;
            }
        }

        if (array == null)
            array = new Object[Utils.nextPowerOf2(source.size())];

        int copiedStart = Integer.MAX_VALUE;

        if (source.getCopied() != null) {
            copiedStart = source.getCopiedStart();
            int length = Math.min(source.getCopied().length, source.size() - copiedStart);

            if (Debug.ENABLED)
                for (int i = length; i < source.getCopied().length; i++)
                    Debug.assertion(source.getCopied()[i] == null);

            for (int i = 0; i < length; i++)
                updateArray(array, copiedStart + i, source.getCopied()[i]);
        }

        if (source.getBits() != null) {
            for (int i = source.getBits().length - 1; i >= 0; i--) {
                if (source.getBits()[i] != null) {
                    int firstWrite = source.getBits()[i].IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                    if (firstWrite < copiedStart) {
                        for (int b = 0; b < Bits.BITS_PER_UNIT; b++) {
                            if (Bits.get(source.getBits()[i].Value, b)) {
                                int index = firstWrite + b;
                                updateArray(array, index, source.get(index));
                            }
                        }
                    }
                }
            }
        }

        if (array != initialArray)
            _array = array;

        if (Debug.ENABLED)
            Debug.assertion(_size + source.getSizeDelta() == source.size());

        for (int i = source.size(); i < _size; i++)
            updateArray(array, i, null);

        _size = source.size();

        return this;
    }

    private final void updateArray(Object[] array, int index, Object value) {
        TObject.Version previous = null;
        UserTObject current = null;

        if (array[index] instanceof TObject.Version)
            previous = (TObject.Version) array[index];

        if (value instanceof UserTObject) {
            current = (UserTObject) value;
            value = current.getSharedVersion_objectfabric();
        }

        updateUserReference(previous, current);
        array[index] = value;
    }

    //

    @Override
    public void visit(com.objectfabric.Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public TListRead createRead() {
        return new TListRead(this);
    }

    @Override
    public TListVersion createVersion() {
        return new TListVersion(this);
    }

    @Override
    public int getClassId() {
        return DefaultObjectModel.COM_OBJECTFABRIC_TLIST_CLASS_ID;
    }

    // User references

    @Override
    protected void recreateUserReferences(Reference reference) {
        super.recreateUserReferences(reference);

        for (int i = 0; i < _size; i++) {
            if (_array[i] instanceof TObject.Version) {
                TObject.Version shared = (TObject.Version) _array[i];
                addUserReference(reference, shared.getOrRecreateTObject());
            }
        }
    }

    // Debug

    @Override
    public boolean hasWritesForDebug() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        /*
         * Does not work as an empty version can be created by merging two non-empty
         * versions.
         */
        return true;
    }

    @Override
    public void checkInvariants_() {
        super.checkInvariants_();

        Object[] array = _array;

        if (array != null)
            for (Object object : array)
                if (object != null)
                    Debug.assertion(!(object instanceof UserTObject));
    }
}