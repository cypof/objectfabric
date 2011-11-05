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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import of4gwt.misc.Debug;
import of4gwt.misc.List;

/**
 * Transactional set. The behavior is as close as possible to the one of an HashSet,
 * except:
 * <nl>
 * - It does not support null elements.
 * <nl>
 * - It does not implement clone().
 * <nl>
 * Elements implementation notes:
 * <nl>
 * If an element's hashCode() or equals(Object) method throws an exception, there is two
 * cases where the exception will be caught (and logged) by ObjectFabric. If the
 * transaction inserting the element is currently committing, and if it is already
 * committed. In the first case the transaction will be aborted. In the second the element
 * will be removed from snapshots of the set seen by transactions started after the
 * exception has been thrown.
 */
@SuppressWarnings("unchecked")
public class TSet<E> extends TKeyed<E> implements Set<E> {

    @SuppressWarnings("hiding")
    public static final TType TYPE = new TType(DefaultObjectModel.getInstance(), DefaultObjectModel.COM_OBJECTFABRIC_TSET_CLASS_ID);

    protected static final String NULL_NOT_SUPPORTED = "TSet elements cannot be null.";

    public TSet() {
        this(Transaction.getDefaultTrunk());
    }

    public TSet(Transaction trunk) {
        super(new SharedVersion(), trunk);
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public TSet(TType genericParam) {
        this(Transaction.getDefaultTrunk(), genericParam);
    }

    public TSet(Transaction trunk, TType genericParam) {
        this(trunk);

        TType[] genericParams = new TType[] { genericParam };
        ((TKeyedSharedVersion) getSharedVersion_objectfabric()).setGenericParameters(genericParams);
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be false. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>addOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public boolean add(E key) {
        if (key == null)
            throw new NullPointerException(NULL_NOT_SUPPORTED);

        Transaction current = Transaction.getCurrent();
        TKeyedEntry entry = new TKeyedEntry(key, hash(key), null, false);
        TKeyedEntry<E, Void> previous = putEntry(key, entry, true, current);
        return current != null && (previous == null || previous.isRemoval());
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void addOnly(E key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry<E, Void>(key, hash(key), null, false);
        putEntry(key, entry, false);
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be false. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>addAllOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAllImpl(c, true);
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void addAllOnly(Collection<? extends E> c) {
        addAllImpl(c, false);
    }

    private final boolean addAllImpl(Collection<? extends E> c, boolean dependency) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);

        if (inner != outer)
            dependency = false;

        boolean result = false, ok = false;

        try {
            for (E element : c) {
                if (element == null)
                    throw new NullPointerException(NULL_NOT_SUPPORTED);

                TKeyedEntry entry = new TKeyedEntry(element, hash(element), null, false);
                TKeyedEntry previous = putEntry(this, inner, element, entry, dependency);

                if (dependency)
                    if (previous == null || previous.isRemoval())
                        result = true;
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return result;
    }

    public void clear() {
        clearTKeyed();
    }

    public boolean contains(Object o) {
        if (o == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(o);
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (E) o, hash);
        } finally {
            Transaction.endRead(outer, inner);
        }

        return entry != null && !entry.isRemoval();
    }

    public boolean containsAll(Collection<?> c) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        boolean result = true;

        try {
            for (Object element : c) {
                if (element == null) {
                    result = false;
                    break;
                }

                TKeyedEntry entry = getEntry(inner, (E) element, hash(element));

                if (entry == null || entry.isRemoval()) {
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

        if (!(o instanceof Set))
            return false;

        Collection c = (Collection) o;

        if (c.size() != size())
            return false;

        return containsAll(c);
    }

    @Override
    public int hashCode() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        KeyedIterator iterator = new KeyedIterator(inner);
        int h = 0;

        try {
            while (iterator.hasNext()) {
                TKeyedEntry entry = iterator.nextEntry();

                if (Debug.ENABLED)
                    Helper.getInstance().disableEqualsOrHashCheck();

                h += entry.getKey().hashCode();

                if (Debug.ENABLED)
                    Helper.getInstance().enableEqualsOrHashCheck();
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        return h;
    }

    public final boolean isEmpty() {
        return size() == 0;
    }

    public Iterator<E> iterator() {
        Transaction current = Transaction.getCurrent();
        return new IteratorImpl(current);
    }

    private final class IteratorImpl extends KeyedIterator implements Iterator<E> {

        public IteratorImpl(Transaction transaction) {
            super(transaction);
        }

        public E next() {
            return (E) nextEntry().getKey();
        }

        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKeyDirect(), current.getHash(), TKeyedEntry.REMOVAL, false);
            TSet.this.putEntry(current.getKeyDirect(), entry, false);
        }
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be false. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>removeOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public boolean remove(Object o) {
        if (o == null)
            throw new NullPointerException(NULL_NOT_SUPPORTED);

        Transaction current = Transaction.getCurrent();
        TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL, false);
        TKeyedEntry<E, Void> previous = putEntry(o, entry, true, current);
        return current != null && (previous != null && !previous.isRemoval());
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void removeOnly(Object o) {
        if (o == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL, false);
        putEntry(o, entry, false);
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

    public int size() {
        return sizeTKeyed();
    }

    public Object[] toArray() {
        List<Object> list = new List<Object>();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);

        for (Object e : this)
            list.add(e);

        Transaction.endRead(outer, inner);
        Object[] array = new Object[list.size()];
        list.copyToFixed(array);
        return array;
    }

    public <T> T[] toArray(T[] array) {
        if (array == null)
            ExpectedExceptionThrower.throwNullPointerException();

        List<T> list = new List<T>();

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);

        for (E e : this)
            list.add((T) e);

        Transaction.endRead(outer, inner);
        return list.copyToWithResizeAndNullEnd(array);
    }

    //

    private static final class SharedVersion extends TKeyedSharedVersion {

        @Override
        public TObject.Version createVersion() {
            return new TKeyedVersion(this);
        }

        @Override
        public int getClassId() {
            return DefaultObjectModel.COM_OBJECTFABRIC_TSET_CLASS_ID;
        }
    }
}
