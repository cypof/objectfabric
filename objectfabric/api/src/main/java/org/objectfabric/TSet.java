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

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

/**
 * Transactional set. For each thread this class behaves like an HashSet, except:<br>
 * <br>
 * - It does not support null elements. <br>
 * - It does not implement clone(). <br>
 * <br>
 * Iterators never throw {@link ConcurrentModificationException}, but currently can offer
 * an inconsistent view of the collection. Items can be missed or provided twice if the
 * collection is updated concurrently. This can be avoided by wrapping the iteration in a
 * {@link Workspace#atomicRead(Runnable)} block, which offers a consistent snapshot of the
 * collection.<br>
 * <br>
 * Some methods on the Set interface both read and write values. E.g. method
 * {@link TSet#add(E)} returns false if the element was present. In the context of a
 * transaction, returning a value induces a read. This read will be checked for conflicts
 * at commit time, and might invalidate the transaction, e.g. if the item has been added
 * or removed concurrently. To avoid this read, transactional collections provide twin
 * methods which do not return values, e.g. {@link TSet#addOnly(E)} which returns void.<br>
 * <br>
 * Keys implementation notes:<br>
 * <br>
 * If a key's hashCode() or equals(Object) method throws an exception, there are two cases
 * where the exception will be caught and logged by ObjectFabric. If the transaction
 * inserting the entry to the map is currently committing, and if it is already committed.
 * In the first case the transaction will be aborted. In the second the entry will be
 * removed from snapshots of the map seen by transactions started after the exception has
 * been thrown.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TSet<E> extends TKeyed<E> implements Set<E> {

    public static final TType TYPE;

    static {
        TYPE = Platform.newTType(Platform.get().defaultObjectModel(), BuiltInClass.TSET_CLASS_ID);
    }

    protected static final String NULL_NOT_SUPPORTED = "TSet elements cannot be null.";

    private final TType[] _genericParameters;

    public TSet(Resource resource) {
        this(resource, null);
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public TSet(Resource resource, TType genericParam) {
        super(resource, new TKeyedSharedVersion());

        if (genericParam == null)
            _genericParameters = null;
        else {
            _genericParameters = Platform.newTTypeArray(1);
            _genericParameters[0] = genericParam;
        }
    }

    @Override
    final TType[] genericParameters() {
        return _genericParameters;
    }

    @Override
    public boolean add(E key) {
        if (key == null)
            throw new NullPointerException(NULL_NOT_SUPPORTED);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), null);
        TKeyedEntry<E, Void> previous = putEntry(entry, true);
        return previous == null || previous.isRemoval();
    }

    /**
     * Does not return a value to avoid a potentially conflicting read. This might improve
     * performance in the context of a transaction.
     */
    public void addOnly(E key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry<E, Void>(key, hash(key), null);
        putEntry(entry, false);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return addAllImpl(c, true);
    }

    /**
     * Does not return a value to avoid a potentially conflicting read. This might improve
     * performance in the context of a transaction.
     */
    public void addAllOnly(Collection<? extends E> c) {
        addAllImpl(c, false);
    }

    private final boolean addAllImpl(Collection<? extends E> c, boolean addRead) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);

        boolean result = false, ok = false;

        try {
            for (E element : c) {
                if (element == null)
                    throw new NullPointerException(NULL_NOT_SUPPORTED);

                TKeyedEntry entry = new TKeyedEntry((E) element, hash(element), null);
                TKeyedEntry previous = putEntry(inner, entry, addRead);

                if (previous == null || previous.isRemoval())
                    result = true;
            }

            ok = true;
        } finally {
            endWrite_(outer, inner, ok);
        }

        return result;
    }

    @Override
    public void clear() {
        clearTKeyed();
    }

    @Override
    public boolean contains(Object o) {
        if (o == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(o);
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (E) o, hash);
        } finally {
            endRead_(outer, inner);
        }

        return entry != null && !entry.isRemoval();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
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
            endRead_(outer, inner);
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
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        KeyedIterator iterator = new KeyedIterator(inner);
        int h = 0;

        try {
            while (iterator.hasNext()) {
                TKeyedEntry entry = iterator.nextEntry();

                if (Debug.ENABLED)
                    Helper.instance().disableEqualsOrHashCheck();

                h += entry.getKey().hashCode();

                if (Debug.ENABLED)
                    Helper.instance().enableEqualsOrHashCheck();
            }
        } finally {
            endRead_(outer, inner);
        }

        return h;
    }

    @Override
    public final boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<E> iterator() {
        Transaction current = current_();
        return new IteratorImpl(current);
    }

    protected class IteratorImpl extends KeyedIterator implements Iterator<E> {

        public IteratorImpl(Transaction transaction) {
            super(transaction);
        }

        @Override
        public E next() {
            return (E) nextEntry().getKey();
        }

        @Override
        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKey(), current.getHash(), TKeyedEntry.REMOVAL);
            TSet.this.putEntry(entry, false);
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o == null)
            throw new NullPointerException(NULL_NOT_SUPPORTED);

        TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL);
        TKeyedEntry<E, Void> previous = putEntry(entry, true);
        return previous != null && !previous.isRemoval();
    }

    /**
     * Does not return a value to avoid a potentially conflicting read. This might improve
     * performance in the context of a transaction.
     */
    public void removeOnly(Object o) {
        if (o == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL);
        putEntry(entry, false);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
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
            endWrite_(outer, inner, ok);
        }

        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
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
            endWrite_(outer, inner, ok);
        }

        return result;
    }

    @Override
    public int size() {
        return sizeTKeyed();
    }

    @Override
    public Object[] toArray() {
        List<Object> list = new List<Object>();

        Transaction outer = current_();
        Transaction inner = startRead_(outer);

        for (Object e : this)
            list.add(e);

        endRead_(outer, inner);
        Object[] array = new Object[list.size()];
        list.copyToFixed(array);
        return array;
    }

    @Override
    public <T> T[] toArray(T[] array) {
        if (array == null)
            ExpectedExceptionThrower.throwNullPointerException();

        List<T> list = new List<T>();

        Transaction outer = current_();
        Transaction inner = startRead_(outer);

        for (E e : this)
            list.add((T) e);

        endRead_(outer, inner);
        return list.copyToWithResizeAndNullEnd(array);
    }

    //

    @Override
    protected int classId_() {
        return BuiltInClass.TSET_CLASS_ID;
    }
}
