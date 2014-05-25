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

import java.util.concurrent.Executor;

public abstract class TIndexed extends TIndexedBase {

    protected TIndexed(Resource resource, TObject.Version shared) {
        super(resource, shared);
    }

    //

    /**
     * Registers a listener to be called when the object changes.
     */
    public final void addListener(IndexListener listener) {
        addListener(listener, workspace().callbackExecutor());
    }

    /**
     * Also specifies on which executor the listener should be invoked.
     */
    public final void addListener(IndexListener listener, Executor executor) {
        workspace().addListener(this, listener, executor);
    }

    public final void removeListener(IndexListener listener) {
        removeListener(listener, workspace().callbackExecutor());
    }

    public final void removeListener(IndexListener listener, Executor executor) {
        workspace().removeListener(this, listener, executor);
    }

    protected final void raiseListener(int fieldIndex) {
        workspace().raiseFieldListener(this, fieldIndex);
    }

    protected final void raiseListener(String propertyName) {
        workspace().raisePropertyListener(this, propertyName);
    }

    //

    abstract int length();

    public final Object getAsObject(int index) {
        if (index < 0 || index >= length())
            throw new IndexOutOfBoundsException();

        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        TIndexed.VersionN version = (TIndexed.VersionN) getVersionN_(inner, index);
        Object value = version.getAsObject(index);
        endRead_(outer, inner);
        return value;
    }

    public final void setAsObject(int index, Object value) {
        if (index < 0 || index >= length())
            throw new IndexOutOfBoundsException();

        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        TIndexed.VersionN version = (TIndexed.VersionN) getOrCreateVersion_(inner);
        version.setBit(index);
        version.setAsObject(index, value);
        endWrite_(outer, inner);
    }

    //

    @Override
    TObject.Version createRead() {
        TObject.Version version = new TIndexedNRead();
        version.setObject(this);
        return version;
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

    static abstract class Read extends TObject.Version {

        public Object getAsObject(int index) {
            throw new IllegalStateException();
        }

        public void setAsObject(int index, Object value) {
            throw new IllegalStateException();
        }

        public String getFieldName(int index) {
            throw new IllegalStateException();
        }

        public TType getFieldType(int index) {
            throw new IllegalStateException();
        }
    }

    public static abstract class Version32 extends TIndexed32Read {

        public Version32(int length) {
        }

        @Override
        final boolean mask(Version version) {
            Version32 v32 = (Version32) version;
            v32.setBits(Bits.andNot(v32.getBits(), getBits()));
            return v32.getBits() == 0;
        }

        @Override
        Version merge(Version target, Version next, boolean threadPrivate) {
            Version32 merged = (Version32) super.merge(target, next, threadPrivate);
            merged.merge(next);
            return merged;
        }

        @Override
        void deepCopy(Version source) {
            super.deepCopy(source);

            merge(source);
        }

        public void merge(Version source) {
        }

        public void writeWrite(Writer writer, int index) {
            throw new IllegalStateException();
        }

        public void readWrite(Reader reader, int index, Object[] versions) {
            throw new IllegalStateException();
        }
    }

    public static abstract class VersionN extends TIndexedNRead {

        public VersionN(int length) {
            /*
             * Shared array must never be reindexed, for other threads visibility, as it
             * is not volatile: allocate necessary length from beginning.
             */
            if (length > 0) {
                int entryCount = Bits.arrayLength(length);
                setBits(new Bits.Entry[Utils.nextPowerOf2(entryCount)], true);
            }
        }

        @Override
        boolean mask(Version version) {
            VersionN vN = (VersionN) version;
            vN.setBits(Bits.andNot(vN.getBits(), getBits()));
            return vN.getBits() == null;
        }

        @Override
        Version merge(Version target, Version next, boolean threadPrivate) {
            VersionN merged = (VersionN) super.merge(target, next, threadPrivate);
            merged.merge(next);
            return merged;
        }

        @Override
        void deepCopy(Version source) {
            super.deepCopy(source);

            merge(source);
        }

        public void merge(Version source) {
        }

        public void writeWrite(Writer writer, int index) {
            throw new IllegalStateException();
        }

        public void readWrite(Reader reader, int index, Object[] versions) {
            throw new IllegalStateException();
        }
    }
}