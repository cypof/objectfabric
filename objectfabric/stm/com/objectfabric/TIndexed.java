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


import com.objectfabric.TIndexedBase;
import com.objectfabric.Visitor.ClassVisitor;
import com.objectfabric.misc.Bits;
import com.objectfabric.misc.Debug;

public abstract class TIndexed extends TIndexedBase {

    static final int NULL_INDEX = -1;

    protected TIndexed(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    //

    public final void addListener(FieldListener listener) {
        addListener(listener, OF.getDefaultAsyncOptions());
    }

    public final void addListener(FieldListener listener, AsyncOptions options) {
        OF.addListener(this, listener, options);
    }

    public final void removeListener(FieldListener listener) {
        removeListener(listener, OF.getDefaultAsyncOptions());
    }

    public final void removeListener(FieldListener listener, AsyncOptions options) {
        OF.removeListener(this, listener, options);
    }

    public final void raiseListener(int fieldIndex, AsyncOptions options) {
        OF.raiseFieldListener(this, fieldIndex, options);
    }

    protected final void raiseListener(String propertyName, AsyncOptions options) {
        OF.raisePropertyListener(this, propertyName, options);
    }

    //

    protected abstract class TIndexedIterator {

        private int _length;

        protected int _cursor = 0;

        public TIndexedIterator(int length) {
            _length = length;
        }

        public final boolean hasNext() {
            if (Debug.ENABLED)
                Debug.assertion(_cursor <= _length);

            return _cursor != _length;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    //

    public static class Visitor extends ClassVisitor {

        public Visitor(com.objectfabric.Visitor visitor) {
            super(visitor);
        }

        @Override
        protected int getId() {
            return com.objectfabric.Visitor.INDEXED_VISITOR_ID;
        }

        protected void onRead(TObject object, int index) {
        }

        protected void onWrite(TObject object, int index) {
        }

        // 32

        void visitTIndexed32(TObject.Version shared, int bits) {
            if (bits != 0) {
                int index = 0;
                TObject object;

                if (getParent().interrupted()) {
                    index = getParent().resumeInt();
                    object = (TObject) getParent().resume();
                } else
                    object = shared.getReference().get();

                if (object != null) {
                    if (getParent().visitingReads()) {
                        for (; index < Integer.SIZE; index++) {
                            if (Bits.get(bits, index)) {
                                onRead(object, index);

                                if (getParent().interrupted()) {
                                    getParent().interrupt(object);
                                    getParent().interruptInt(index);
                                    return;
                                }
                            }
                        }
                    } else {
                        TIndexed32Version version = (TIndexed32Version) shared;

                        for (; index < version.length(); index++) {
                            if (Bits.get(bits, index)) {
                                onWrite(object, index);

                                if (getParent().interrupted()) {
                                    getParent().interrupt(object);
                                    getParent().interruptInt(index);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }

        // N

        void visitTIndexedN(TObject.Version shared, Bits.Entry[] bits) {
            if (bits != null) {
                int index = 0, bit = 0;
                TObject object;

                if (getParent().interrupted()) {
                    index = getParent().resumeInt();
                    bit = getParent().resumeInt();
                    object = (TObject) getParent().resume();
                } else
                    object = shared.getReference().get();

                if (object != null) {
                    for (; index < bits.length; index++) {
                        if (bits[index] != null) {
                            int offset = bits[index].IntIndex << Bits.BITS_PER_UNIT_SHIFT;

                            for (; bit < Bits.BITS_PER_UNIT; bit++) {
                                if (Bits.get(bits[index].Value, bit)) {
                                    int actualIndex = offset + bit;

                                    if (getParent().visitingReads())
                                        onRead(object, actualIndex);
                                    else
                                        onWrite(object, actualIndex);

                                    if (getParent().interrupted()) {
                                        getParent().interrupt(object);
                                        getParent().interruptInt(bit);
                                        getParent().interruptInt(index);
                                        return;
                                    }
                                }
                            }

                            bit = 0;
                        }
                    }
                }
            }
        }
    }
}