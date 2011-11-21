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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import com.objectfabric.misc.Debug;

/**
 * Transactional list. This class is designed to behave as much as possible like an
 * ArrayList, but does not implement clone and is not serializable.
 * <nl>
 * Some methods on the List interface both read and write values. E.g. the remove(int)
 * method returns the removed object. The implementations of those methods do not exhibit
 * the same behavior if called in the context of a transaction, or if they have to start
 * an implicit one. In a distributed setting, the server receives updates concurrently
 * from several clients. The object present at the removed location will be known only
 * once the remove is ordered amongst others changes on the server. Network operations
 * might take a long time, and the method does not offer a way to provide an asynchronous
 * callback to return the value later. It would be too slow to block the current thread to
 * wait for the server on each method call. Instead, the method does not wait and always
 * returns null.
 * <nl>
 * To generalize, all methods on transactional collections that return a value as a side
 * effect of updating an object have this behavior. They return default values like false
 * or null if not called in the context of a transaction. When called in the context of a
 * transaction, return values are meaningful. However, the transaction will be aborted
 * later if the server finds that another update has been made concurrently. To avoid
 * aborts, if you do not need the return value from a method, ObjectFabric provides twin
 * methods which do not return values, e.g. addOnly which return void.
 */
@SuppressWarnings("unchecked")
public class TList<E> extends TIndexed implements List<E>, RandomAccess {

    @SuppressWarnings("hiding")
    public static final TType TYPE = new TType(DefaultObjectModel.getInstance(), DefaultObjectModel.COM_OBJECTFABRIC_TLIST_CLASS_ID);

    public TList() {
        this(Transaction.getDefaultTrunk());
    }

    public TList(Transaction trunk) {
        super(new TListSharedVersion(), trunk);
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public TList(TType genericParam) {
        this(Transaction.getDefaultTrunk(), genericParam);
    }

    public TList(Transaction trunk, TType genericParam) {
        this(trunk);

        TType[] genericParams = new TType[] { genericParam };
        ((TListSharedVersion) getSharedVersion_objectfabric()).setGenericParameters(genericParams);
    }

    public boolean add(E e) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = getOrCreateVersion(inner);
        int index = size(inner, true, version);
        version.insert(index);
        version.set(index, e);
        Transaction.endWrite(outer, inner);
        return true;
    }

    public void add(int index, E e) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);

        if (index < 0 || index > size(inner, false, version)) {
            Transaction.endWrite(outer, inner, false);
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();
        }

        if (version == null) {
            version = (TListVersion) getSharedVersion_objectfabric().createVersion();
            inner.putVersion(this, version);
        }

        version.insert(index);
        version.set(index, e);
        Transaction.endWrite(outer, inner);
    }

    public boolean addAll(Collection<? extends E> c) {
        if (c == null) {
            ExpectedExceptionThrower.throwNullPointerException();
            return false;
        }

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);
        int index = size(inner, true, version);
        boolean ok = false, modified = false;

        try {
            for (E e : c) {
                if (version == null) {
                    version = (TListVersion) getSharedVersion_objectfabric().createVersion();
                    inner.putVersion(this, version);
                }

                version.insert(index);
                version.set(index++, e);
                modified = true;
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return modified;
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        if (c == null) {
            ExpectedExceptionThrower.throwNullPointerException();
            return false;
        }

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);

        if (index < 0 || index > size(inner, false, version)) {
            Transaction.endWrite(outer, inner, false);
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();
        }

        boolean ok = false, modified = false;

        try {
            for (E e : c) {
                if (version == null) {
                    version = (TListVersion) getSharedVersion_objectfabric().createVersion();
                    inner.putVersion(this, version);
                }

                version.insert(index);
                version.set(index++, e);
                modified = true;
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return modified;
    }

    public void clear() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = getOrCreateVersion(inner);
        version.clearCollection();
        Transaction.endWrite(outer, inner);
    }

    public boolean contains(Object o) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        IteratorImpl it = new IteratorImpl(inner);
        boolean result = false;

        try {
            if (o == null) {
                while (it.hasNext()) {
                    if (it.next() == null) {
                        result = true;
                        break;
                    }
                }
            } else {
                while (it.hasNext()) {
                    if (Debug.ENABLED)
                        Helper.getInstance().disableEqualsOrHashCheck();

                    boolean equal = o.equals(it.next());

                    if (Debug.ENABLED)
                        Helper.getInstance().enableEqualsOrHashCheck();

                    if (equal) {
                        result = true;
                        break;
                    }
                }
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        return result;
    }

    public boolean containsAll(Collection<?> c) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        boolean result = true;

        try {
            for (Object o : c) {
                if (!contains(o)) {
                    result = false;
                    break;
                }
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof List))
            return false;

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        IteratorImpl it1 = new IteratorImpl(inner);
        boolean result = true;

        try {
            ListIterator it2 = ((List) o).listIterator();

            while (it1.hasNext() && it2.hasNext()) {
                E o1 = it1.next();
                Object o2 = it2.next();

                if (!(o1 == null ? o2 == null : o1.equals(o2))) {
                    result = false;
                    break;
                }
            }

            if (result)
                result = !(it1.hasNext() || it2.hasNext());
        } finally {
            Transaction.endRead(outer, inner);
        }

        return result;
    }

    public E get(int index) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);

        if (index < 0 || index >= size(inner, false, version)) {
            Transaction.endRead(outer, inner);
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();
        }

        E result = get(index, inner, version, true);
        Transaction.endRead(outer, inner);
        return result;
    }

    private final E get(int index, Transaction transaction, TListVersion version, boolean record) {
        E value = getImpl(index, transaction, version, record);

        if (Debug.ENABLED)
            Debug.assertion(!(value instanceof TObject.Version));

        return value;
    }

    private final E getImpl(int index, Transaction transaction, TListVersion version, boolean record) {
        int offsetIndex = index;

        if (version != null) {
            if (version.getBit(offsetIndex))
                return (E) version.get(offsetIndex);

            if (version.getCleared())
                return null;

            offsetIndex = version.offset(offsetIndex);
        }

        // Private versions
        {
            Version[][] versions = transaction.getPrivateSnapshotVersions();

            if (versions != null) {
                for (int i = versions.length - 1; i >= 0; i--) {
                    TListVersion current = (TListVersion) TransactionSets.getVersionFromTObject(versions[i], this);

                    if (current != null) {
                        if (current.getBit(offsetIndex))
                            return (E) current.get(offsetIndex);

                        if (current.getCleared())
                            return null;

                        offsetIndex = current.offset(offsetIndex);
                    }
                }
            }
        }

        if (record)
            if (!transaction.noReads())
                markRead(transaction);

        // Public versions
        {
            Version[][] versions = transaction.getPublicSnapshotVersions();

            for (int i = versions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
                TListVersion current = (TListVersion) TransactionSets.getVersionFromTObject(versions[i], this);

                if (current != null) {
                    if (current.getBit(offsetIndex))
                        return (E) current.get(offsetIndex);

                    if (current.getCleared())
                        return null;

                    Object[] copied = current.getCopied();

                    if (copied != null) {
                        int copiedIndex = offsetIndex - current.getCopiedStart();

                        if (copiedIndex >= 0)
                            return (E) current.getCopied()[copiedIndex];
                    }

                    offsetIndex = current.offset(offsetIndex);
                }
            }
        }

        E value = (E) ((TListSharedVersion) getSharedVersion_objectfabric()).get(offsetIndex);

        if (value instanceof TObject.Version)
            value = (E) ((TObject.Version) value).getUserTObject_objectfabric();

        return value;
    }

    /**
     * For publishing, after private versions have been merged.
     */
    static final <E> E get(int index, Snapshot snapshot, int mapIndex, TListVersion version) {
        int offsetIndex = index;

        if (version.getBit(offsetIndex))
            return (E) version.get(offsetIndex);

        if (version.getCleared())
            return null;

        offsetIndex = version.offset(offsetIndex);

        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TListVersion current = (TListVersion) TransactionSets.getVersionFromSharedVersion(snapshot.getWrites()[i], version.getUnion());

            if (current != null) {
                if (current.getBit(offsetIndex))
                    return (E) current.get(offsetIndex);

                if (current.getCleared())
                    return null;

                Object[] copied = current.getCopied();

                if (copied != null) {
                    int copiedIndex = offsetIndex - current.getCopiedStart();

                    if (copiedIndex >= 0)
                        return (E) current.getCopied()[copiedIndex];
                }

                offsetIndex = current.offset(offsetIndex);
            }
        }

        E value = (E) ((TListSharedVersion) version.getUnion()).get(offsetIndex);

        if (value instanceof TObject.Version)
            value = (E) ((TObject.Version) value).getUserTObject_objectfabric();

        return value;
    }

    @Override
    public int hashCode() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        int hashCode = 1;

        try {
            for (E e : this)
                hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
        } finally {
            Transaction.endRead(outer, inner);
        }

        return hashCode;
    }

    public int indexOf(Object o) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        int result;

        try {
            result = indexOf(o, inner);
        } finally {
            Transaction.endRead(outer, inner);
        }

        return result;
    }

    private final int indexOf(Object o, Transaction transaction) {
        IteratorImpl i = new IteratorImpl(transaction);

        if (o == null) {
            while (i.hasNext())
                if (i.next() == null)
                    return i.previousIndex();
        } else {
            while (i.hasNext())
                if (o.equals(i.next()))
                    return i.previousIndex();
        }

        return -1;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int lastIndexOf(Object o) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        IteratorImpl i = new IteratorImpl(inner, true, -1);

        try {
            if (o == null) {
                while (i.hasPrevious())
                    if (i.previous() == null)
                        return i.nextIndex();
            } else {
                while (i.hasPrevious())
                    if (o.equals(i.previous()))
                        return i.nextIndex();
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        return -1;
    }

    public Iterator<E> iterator() {
        return new IteratorImpl(Transaction.getCurrent());
    }

    public ListIterator<E> listIterator() {
        return new IteratorImpl(Transaction.getCurrent());
    }

    public ListIterator<E> listIterator(int index) {
        if (index < 0)
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();

        return new IteratorImpl(Transaction.getCurrent(), true, index);
    }

    private final class IteratorImpl implements ListIterator<E> {

        private int _cursor;

        private boolean _record;

        private final Transaction _transaction;

        private final Snapshot _initialSnapshot;

        public IteratorImpl(Transaction transaction) {
            this(transaction, true, 0);
        }

        public IteratorImpl(Transaction transaction, boolean record, int index) {
            _transaction = Transaction.startIteration(transaction, TList.this);
            _initialSnapshot = _transaction.getSnapshot();
            _record = record;

            TListVersion version = (TListVersion) _transaction.getVersionFromTObject(TList.this);

            if (record)
                onSizeRead(_transaction, version);

            if (index < 0) {
                _cursor = sizeNoCheck(_transaction, record, version) - 1;
            } else
                _cursor = index;
        }

        public boolean hasNext() {
            if (_transaction.getSnapshot() != _initialSnapshot)
                throw new RuntimeException(Strings.ITERATORS);

            TListVersion version = (TListVersion) _transaction.getVersionFromTObject(TList.this);
            return _cursor < sizeNoCheck(_transaction, _record, version);
        }

        public E next() {
            if (_transaction.getSnapshot() != _initialSnapshot)
                throw new RuntimeException(Strings.ITERATORS);

            TListVersion version = (TListVersion) _transaction.getVersionFromTObject(TList.this);

            if (_cursor >= sizeNoCheck(_transaction, _record, version))
                throw new NoSuchElementException();

            return get(_cursor++, _transaction, version, _record);
        }

        //

        public void add(E e) {
            TList.this.add(_cursor++, e);
        }

        public void remove() {
            TList.this.remove(--_cursor);
        }

        public void set(E e) {
            TList.this.set(_cursor - 1, e);
        }

        //

        public boolean hasPrevious() {
            return _cursor > 0;
        }

        public int nextIndex() {
            return _cursor + 1;
        }

        public E previous() {
            if (_cursor <= 0)
                throw new NoSuchElementException();

            return get(_cursor--);
        }

        public int previousIndex() {
            return _cursor - 1;
        }
    }

    public boolean remove(Object o) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        boolean ok = false, result = false;

        try {
            int index = indexOf(o, inner);

            if (index >= 0) {
                TListVersion version = getOrCreateVersion(inner);

                if (Debug.ENABLED)
                    Debug.assertion(index < size(inner, false, version));

                version.remove(index);
                result = true;
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return result;
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be null. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>removeOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public E remove(int index) {
        return remove(index, true);
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void removeOnly(int index) {
        remove(index, false);
    }

    private final E remove(int index, boolean read) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);

        if (index < 0 || index >= size(inner, false, version)) {
            Transaction.endWrite(outer, inner, false);
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();
        }

        if (version == null) {
            version = (TListVersion) getSharedVersion_objectfabric().createVersion();
            inner.putVersion(this, version);
        }

        E result = null;

        if (read)
            result = get(index, inner, version, true);

        version.remove(index);
        Transaction.endWrite(outer, inner);
        return result;
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be false. Otherwise, a read is performed to return the previous value.
     * <p>
     * {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        boolean modified = false, ok = false;
        IteratorImpl it = new IteratorImpl(inner);

        try {
            while (it.hasNext()) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return modified && outer == inner;
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be false. Otherwise, a read is performed to return the previous value.
     * <p>
     * {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        boolean ok = false, result = false;
        IteratorImpl it = new IteratorImpl(inner);

        try {
            while (it.hasNext()) {
                if (!c.contains(it.next())) {
                    it.remove();
                    result = true;
                }
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return result && outer == inner;
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be null. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>setOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public E set(int index, E value) {
        return set(index, value, true);
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void setOnly(int index, E value) {
        set(index, value, false);
    }

    private final E set(int index, E value, boolean read) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TListVersion version = (TListVersion) inner.getVersionFromTObject(this);

        if (index < 0 || index >= size(inner, false, version)) {
            Transaction.endWrite(outer, inner, false);
            ExpectedExceptionThrower.throwIndexOutOfBoundsException();
        }

        if (version == null) {
            version = (TListVersion) getSharedVersion_objectfabric().createVersion();
            inner.putVersion(this, version);
        }

        E result = null;

        if (read)
            result = get(index, inner, version, true);

        version.setBit(index);
        version.set(index, value);
        Transaction.endWrite(outer, inner);
        return result;
    }

    public int size() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        int result = size(inner, true);
        Transaction.endRead(outer, inner);
        return result;
    }

    private final int size(Transaction transaction, boolean record) {
        TListVersion version = (TListVersion) transaction.getVersionFromTObject(this);
        return size(transaction, record, version);
    }

    private final int size(Transaction transaction, boolean record, TListVersion version) {
        int size = sizeNoCheck(transaction, record, version);

        if (Debug.ENABLED) {
            IteratorImpl iterator = new IteratorImpl(transaction, false, 0);

            int count = 0;

            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }

            Debug.assertion(size == count);
        }

        return size;
    }

    private final int sizeNoCheck(Transaction transaction, boolean record, TListVersion version) {
        if (Debug.ENABLED)
            Debug.assertion(!transaction.isPublic());

        int size = 0;

        if (version != null) {
            if (Debug.ENABLED)
                Debug.assertion(!version.sizeValid() || transaction.isCommitted());

            size = version.getSizeDelta();

            if (version.getCleared())
                return size;
        }

        TObject.Version[][] versions = transaction.getPrivateSnapshotVersions();

        if (versions != null) {
            for (int i = versions.length - 1; i >= 0; i--) {
                TListVersion current = (TListVersion) TransactionSets.getVersionFromTObject(versions[i], this);

                if (current != null) {
                    if (Debug.ENABLED)
                        Debug.assertion(!current.sizeValid() || transaction.isCommitted());

                    size += current.getSizeDelta();

                    if (current.getCleared())
                        return size;
                }
            }
        }

        if (record)
            onSizeRead(transaction, version);

        versions = transaction.getPublicSnapshotVersions();

        for (int i = versions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TListVersion current = (TListVersion) TransactionSets.getVersionFromTObject(versions[i], this);

            if (current != null)
                return current.size() + size;
        }

        TListSharedVersion shared = (TListSharedVersion) getSharedVersion_objectfabric();
        size += shared.size();
        return size;
    }

    private final void onSizeRead(Transaction transaction, TListVersion version) {
        if (!transaction.noReads())
            if (version == null || !version.getCleared())
                markRead(transaction);
    }

    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        com.objectfabric.misc.List<Object> list = new com.objectfabric.misc.List<Object>();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);

        for (E e : this)
            list.add(e);

        Transaction.endRead(outer, inner);
        Object[] array = new Object[list.size()];
        list.copyToFixed(array);
        return array;
    }

    public <T> T[] toArray(T[] array) {
        if (array == null)
            ExpectedExceptionThrower.throwNullPointerException();

        com.objectfabric.misc.List<T> list = new com.objectfabric.misc.List<T>();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);

        for (E e : this)
            list.add((T) e);

        Transaction.endRead(outer, inner);
        return list.copyToWithResizeAndNullEnd(array);
    }

    //

    public void addListener(ListListener listener) {
        addListener(listener, OF.getDefaultAsyncOptions());
    }

    public void addListener(ListListener listener, AsyncOptions options) {
        OF.addListener(this, listener, options);
    }

    public void removeListener(ListListener listener) {
        removeListener(listener, OF.getDefaultAsyncOptions());
    }

    public void removeListener(ListListener listener, AsyncOptions options) {
        OF.removeListener(this, listener, options);
    }

    //

    public static class Visitor extends TIndexed.Visitor {

        public Visitor(com.objectfabric.Visitor visitor) {
            super(visitor);
        }

        @Override
        protected int getId() {
            return com.objectfabric.Visitor.INDEXED_VISITOR_ID;
        }

        // Reads

        /**
         * @param object
         */
        protected void onRead(TObject object) {
        }

        // Writes

        /**
         * @param object
         * @param index
         */
        protected void onAdd(TObject object, int index) {
        }

        /**
         * @param object
         * @param index
         */
        protected void onRemoval(TObject object, int index) {
        }

        /**
         * @param object
         */
        protected void onClear(TObject object) {
        }

        //

        protected void visitRead(TListSharedVersion shared) {
            TObject object;

            if (getParent().interrupted())
                object = (TObject) getParent().resume();
            else
                object = shared.getReference().get();

            if (object != null) {
                onRead(object);

                if (getParent().interrupted()) {
                    getParent().interrupt(object);
                    return;
                }
            }
        }

        protected void visitGatheredWrites(TListSharedVersion version, Object[] array, int size) {
            if (array != null) {
                int index = 0;
                TObject object;

                if (getParent().interrupted()) {
                    index = getParent().resumeInt();
                    object = (TObject) getParent().resume();
                } else
                    object = version.getShared().getReference().get();

                if (object != null) {
                    for (; index < size; index++) {
                        if (array[index] != null) {
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

        private enum ListStep {
            CLEAR, REMOVALS, INSERTS, VALUES
        }

        @SuppressWarnings("fallthrough")
        protected void visitVersion(TListVersion version) {
            ListStep step = ListStep.CLEAR;
            int index = 0;
            int[] removals = null, inserts = null;
            TObject object;
            Version shared = (Version) version.getUnion();

            if (getParent().interrupted()) {
                step = (ListStep) getParent().resume();
                index = getParent().resumeInt();
                removals = (int[]) getParent().resume();
                inserts = (int[]) getParent().resume();
                object = (TObject) getParent().resume();
            } else {
                if (version.getRemovalsCount() != 0)
                    removals = version.getRemovalsReindexedAsPrevious(0);

                if (version.getInsertsCount() != 0)
                    inserts = version.getInsertsReindexedAsPrevious(0);

                object = shared.getReference().get();
            }

            if (object != null) {
                switch (step) {
                    case CLEAR: {
                        if (version.getCleared()) {
                            onClear(object);

                            if (getParent().interrupted()) {
                                getParent().interrupt(object);
                                getParent().interrupt(inserts);
                                getParent().interrupt(removals);
                                getParent().interruptInt(index);
                                getParent().interrupt(ListStep.CLEAR);
                                return;
                            }
                        }
                    }
                    case REMOVALS: {
                        if (removals != null) {
                            for (; index < removals.length; index++) {
                                onRemoval(object, removals[index]);

                                if (getParent().interrupted()) {
                                    getParent().interrupt(object);
                                    getParent().interrupt(inserts);
                                    getParent().interrupt(removals);
                                    getParent().interruptInt(index);
                                    getParent().interrupt(ListStep.REMOVALS);
                                    return;
                                }
                            }

                            index = 0;
                        }
                    }
                    case INSERTS: {
                        if (inserts != null) {
                            for (; index < inserts.length; index++) {
                                onAdd(object, inserts[index]);

                                if (getParent().interrupted()) {
                                    getParent().interrupt(object);
                                    getParent().interrupt(inserts);
                                    getParent().interrupt(removals);
                                    getParent().interruptInt(index);
                                    getParent().interrupt(ListStep.INSERTS);
                                    return;
                                }
                            }
                        }
                    }
                    case VALUES: {
                        visitTIndexedN(shared, version.getBits());

                        if (getParent().interrupted()) {
                            getParent().interrupt(object);
                            getParent().interrupt(inserts);
                            getParent().interrupt(removals);
                            getParent().interruptInt(index);
                            getParent().interrupt(ListStep.VALUES);
                            return;
                        }
                    }
                }
            }
        }
    }

    //

    final void markRead(Transaction transaction) {
        TListRead read = (TListRead) transaction.getRead(this);

        if (read == null) {
            read = (TListRead) getSharedVersion_objectfabric().createRead();
            transaction.putRead(this, read);
        }
    }

    final TListVersion getOrCreateVersion(Transaction transaction) {
        TListVersion version = (TListVersion) transaction.getVersionFromTObject(this);

        if (version == null) {
            version = (TListVersion) getSharedVersion_objectfabric().createVersion();
            transaction.putVersion(this, version);
        }

        return version;
    }
}