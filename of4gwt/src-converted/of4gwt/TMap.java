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
import java.util.Map;
import java.util.Set;

/**
 * Transactional map. The behavior is as close as possible to the one of an HashMap,
 * except:
 * <nl>
 * - It does not support null keys.
 * <nl>
 * - It does not implement clone().
 * <nl>
 * - Entries do not support setValue(V).
 * <nl>
 * Keys implementation notes:
 * <nl>
 * If a key's hashCode() or equals(Object) method throws an exception, there is two cases
 * where the exception will be caught (and logged) by ObjectFabric. If the transaction
 * inserting the entry to the map is currently committing, and if it is already committed.
 * In the first case the transaction will be aborted. In the second the entry will be
 * removed from snapshots of the map seen by transactions started after the exception has
 * been thrown.
 */
@SuppressWarnings("unchecked")
public class TMap<K, V> extends TKeyed<K> implements Map<K, V> {

    @SuppressWarnings("hiding")
    public static final TType TYPE = new TType(DefaultObjectModel.getInstance(), DefaultObjectModel.COM_OBJECTFABRIC_TMAP_CLASS_ID);

    public TMap() {
        this(Transaction.getDefaultTrunk());
    }

    public TMap(Transaction trunk) {
        this(new SharedVersion(), trunk);
    }

    protected TMap(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public TMap(TType genericParamKey, TType genericParamValue) {
        this(Transaction.getDefaultTrunk(), genericParamKey, genericParamValue);
    }

    public TMap(Transaction trunk, TType genericParamKey, TType genericParamValue) {
        this(trunk);

        TType[] genericParams = new TType[] { genericParamKey, genericParamValue };
        ((TKeyedSharedVersion) getSharedVersion_objectfabric()).setGenericParameters(genericParams);
    }

    public void clear() {
        clearTKeyed();
    }

    public boolean containsKey(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(key);
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (K) key, hash);
        } finally {
            Transaction.endRead(outer, inner);
        }

        return entry != null && !entry.isRemoval();
    }

    public boolean containsValue(Object value) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        KeyedIterator iterator = new KeyedIterator(inner);
        boolean result = false;

        try {
            while (iterator.hasNext()) {
                Object object = iterator.nextEntry().getValue();

                if (value == object) {
                    result = true;
                    break;
                }

                if (value != null && value.equals(object)) {
                    result = true;
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

        if (!(o instanceof Map))
            return false;

        Map<K, V> map = (Map<K, V>) o;
        int size = 0;

        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        KeyedIterator iterator = new KeyedIterator(inner);

        try {
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.nextEntry();
                K key = entry.getKey();
                V value = entry.getValue();

                if (value == null) {
                    if (!(map.get(key) == null && map.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(map.get(key)))
                        return false;
                }

                size++;
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        if (map.size() != size)
            return false;

        return true;
    }

    public V get(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(key);
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (K) key, hash);
        } finally {
            Transaction.endRead(outer, inner);
        }

        return entry != null && !entry.isRemoval() ? (V) entry.getValue() : null;
    }

    @Override
    public int hashCode() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        KeyedIterator iterator = new KeyedIterator(inner);
        int h = 0;

        try {
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.nextEntry();
                h += entry.hashCode();
            }
        } finally {
            Transaction.endRead(outer, inner);
        }

        return h;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be null. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>putOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public V put(K key, V value) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        Transaction current = Transaction.getCurrent();
        TKeyedEntry entry = new TKeyedEntry(key, hash(key), value, false);
        TKeyedEntry<K, V> previous = putEntry(key, entry, true, current);
        return current != null && previous != null && !previous.isRemoval() ? previous.getValue() : null;
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void putOnly(K key, V value) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry<K, V>(key, hash(key), value, false);
        putEntry(key, entry, false);
    }

    public void putAll(Map<? extends K, ? extends V> t) {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        boolean ok = false;

        try {
            for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
                if (entry.getKey() == null)
                    throw new NullPointerException(Strings.ARGUMENT_NULL);

                putEntry(this, inner, entry.getKey(), new TKeyedEntry(entry.getKey(), hash(entry.getKey()), entry.getValue(), false), false);
            }

            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }
    }

    /**
     * If this method is not called in the context of a transaction, the return value will
     * always be null. Otherwise, a read is performed to return the previous value. Reads
     * can cause a transaction to abort, so use <code>removeOnly</code> if you do not need
     * the return value.
     * <p>
     * {@inheritDoc}
     */
    public V remove(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        Transaction current = Transaction.getCurrent();
        TKeyedEntry entry = new TKeyedEntry(key, hash(key), TKeyedEntry.REMOVAL, false);
        TKeyedEntry<K, V> previous = putEntry(key, entry, true, current);
        return current != null && previous != null && !previous.isRemoval() ? previous.getValue() : null;
    }

    /**
     * Does not return a value to avoid a potentially conflicting read.
     */
    public void removeOnly(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), TKeyedEntry.REMOVAL, false);
        putEntry(key, entry, false);
    }

    //

    public int size() {
        return sizeTKeyed();
    }

    // Views

    public Set<K> keySet() {
        return new KeySet();
    }

    private final class KeySet implements Set<K> {

        public boolean add(K e) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public boolean addAll(Collection<? extends K> c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public boolean containsAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);
            boolean result = true;

            try {
                for (Object element : c) {
                    if (element == null) {
                        result = false;
                        break;
                    }

                    TKeyedEntry entry = getEntry(inner, (K) element, hash(element));

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

        public void clear() {
            TMap.this.clear();
        }

        public boolean contains(Object o) {
            return TMap.this.containsKey(o);
        }

        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        public Iterator<K> iterator() {
            Transaction transaction = Transaction.getCurrent();
            return new KeyIterator(transaction);
        }

        public boolean remove(Object o) {
            if (o == null)
                throw new NullPointerException(Strings.ARGUMENT_NULL);

            Transaction current = Transaction.getCurrent();
            TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL, false);
            TKeyedEntry<K, V> previous = TMap.this.putEntry(o, entry, true, current);
            return current != null && previous != null && !previous.isRemoval();
        }

        public boolean removeAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startWrite(outer, TMap.this);
            boolean modified = false, ok = false;
            KeyIterator it = new KeyIterator(inner);

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

        public boolean retainAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startWrite(outer, TMap.this);
            boolean modified = false, ok = false;
            KeyIterator it = new KeyIterator(inner);

            try {
                while (it.hasNext()) {
                    if (!c.contains(it.next())) {
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

        public int size() {
            return TMap.this.size();
        }

        public Object[] toArray() {
            of4gwt.misc.List<Object> list = new of4gwt.misc.List<Object>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (K k : this)
                list.add(k);

            Transaction.endRead(outer, inner);
            Object[] array = new Object[list.size()];
            list.copyToFixed(array);
            return array;
        }

        public <T> T[] toArray(T[] array) {
            if (array == null)
                ExpectedExceptionThrower.throwNullPointerException();

            of4gwt.misc.List<T> list = new of4gwt.misc.List<T>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (K k : this)
                list.add((T) k);

            Transaction.endRead(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class KeyIterator extends KeyedIterator implements Iterator<K> {

        public KeyIterator(Transaction transaction) {
            super(transaction);
        }

        public K next() {
            return (K) nextEntry().getKey();
        }

        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKeyDirect(), current.getHash(), TKeyedEntry.REMOVAL, false);
            TMap.this.putEntry(current.getKeyDirect(), entry, false);
        }
    }

    public Collection<V> values() {
        return new Values();
    }

    private final class Values implements Collection<V> {

        public boolean add(V e) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public boolean addAll(Collection<? extends V> c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public void clear() {
            TMap.this.clear();
        }

        public boolean contains(Object o) {
            return TMap.this.containsValue(o);
        }

        public boolean containsAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);
            boolean result = true;

            try {
                for (Object element : c) {
                    if (!TMap.this.containsValue(element)) {
                        result = false;
                        break;
                    }
                }
            } finally {
                Transaction.endRead(outer, inner);
            }

            return result;
        }

        public Iterator<V> iterator() {
            Transaction transaction = Transaction.getCurrent();
            return new ValueIterator(transaction);
        }

        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        public boolean remove(Object o) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startWrite(outer, TMap.this);
            boolean ok = false, result = false;

            try {
                Iterator it = new ValueIterator(inner);

                if (o == null) {
                    while (it.hasNext()) {
                        if (it.next() == null) {
                            it.remove();
                            result = true;
                            break;
                        }
                    }
                } else {
                    while (it.hasNext()) {
                        if (o.equals(it.next())) {
                            it.remove();
                            result = true;
                            break;
                        }
                    }
                }

                ok = true;
            } finally {
                Transaction.endWrite(outer, inner, ok);
            }

            return result;
        }

        public boolean removeAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startWrite(outer, TMap.this);
            boolean modified = false, ok = false;
            ValueIterator it = new ValueIterator(inner);

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

        public boolean retainAll(Collection<?> c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startWrite(outer, TMap.this);
            boolean modified = false, ok = false;
            ValueIterator it = new ValueIterator(inner);

            try {
                while (it.hasNext()) {
                    if (!c.contains(it.next())) {
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

        public int size() {
            return TMap.this.size();
        }

        public Object[] toArray() {
            of4gwt.misc.List<Object> list = new of4gwt.misc.List<Object>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (V e : this)
                list.add(e);

            Transaction.endRead(outer, inner);
            Object[] array = new Object[list.size()];
            list.copyToFixed(array);
            return array;
        }

        public <T> T[] toArray(T[] array) {
            if (array == null)
                ExpectedExceptionThrower.throwNullPointerException();

            of4gwt.misc.List<T> list = new of4gwt.misc.List<T>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (V e : this)
                list.add((T) e);

            Transaction.endRead(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class ValueIterator extends KeyedIterator implements Iterator<V> {

        public ValueIterator(Transaction transaction) {
            super(transaction);
        }

        public V next() {
            return (V) nextEntry().getValue();
        }

        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKeyDirect(), current.getHash(), TKeyedEntry.REMOVAL, false);
            TMap.this.putEntry(current.getKeyDirect(), entry, false);
        }
    }

    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet implements Set {

        public boolean add(Object o) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public boolean addAll(Collection c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        public void clear() {
            TMap.this.clear();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;

            Entry<K, V> given = (Entry<K, V>) o;
            K key = given.getKey();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);
            TKeyedEntry stored;

            try {
                stored = getEntry(inner, key, hash(key));
            } finally {
                Transaction.endRead(outer, inner);
            }

            return stored != null && stored.equals(given);
        }

        public boolean containsAll(Collection c) {
            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);
            boolean result = true;

            try {
                for (Object element : c) {
                    if (!contains(element)) {
                        result = false;
                        break;
                    }
                }
            } finally {
                Transaction.endRead(outer, inner);
            }

            return result;
        }

        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        public Iterator iterator() {
            Transaction transaction = Transaction.getCurrent();
            return new EntryIterator(transaction);
        }

        public boolean remove(Object o) {
            if (o == null)
                throw new NullPointerException(Strings.ARGUMENT_NULL);

            Transaction current = Transaction.getCurrent();

            /*
             * Otherwise in a distributed setting this would require either blocking until
             * response from coordinator or serializing the whole entry to compare it
             * remotely.
             */
            if (current == null)
                throw new RuntimeException(Strings.CURRENT_NULL);

            if (current.getTrunk() != getTrunk())
                throw new IllegalArgumentException(Strings.WRONG_TRUNK);

            if (!(o instanceof Entry))
                return false;

            Entry<K, V> given = (Entry<K, V>) o;
            K key = given.getKey();
            int hash = hash(key);
            boolean changed = false;

            TKeyedEntry stored = getEntry(current, key, hash);

            if (stored != null && stored.equals(given)) {
                TKeyedEntry entry = new TKeyedEntry(key, hash, TKeyedEntry.REMOVAL, false);
                TMap.this.putEntry(key, entry, false, current);
                changed = true;
            }

            return changed;
        }

        public boolean removeAll(Collection c) {
            if (c == null)
                throw new NullPointerException();

            Transaction current = Transaction.getCurrent();

            /*
             * Otherwise in a distributed setting this would require either blocking until
             * response from coordinator or serializing the whole entry to compare it
             * remotely.
             */
            if (current == null)
                throw new RuntimeException(Strings.CURRENT_NULL);

            if (current.getTrunk() != getTrunk())
                throw new IllegalArgumentException(Strings.WRONG_TRUNK);

            boolean modified = false;
            EntryIterator it = new EntryIterator(current);

            while (it.hasNext()) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }

            return modified;
        }

        public boolean retainAll(Collection c) {
            if (c == null)
                throw new NullPointerException();

            Transaction current = Transaction.getCurrent();

            /*
             * Otherwise in a distributed setting this would require either blocking until
             * response from coordinator or serializing the whole entry to compare it
             * remotely.
             */
            if (current == null)
                throw new RuntimeException(Strings.CURRENT_NULL);

            if (current.getTrunk() != getTrunk())
                throw new IllegalArgumentException(Strings.WRONG_TRUNK);

            boolean modified = false;
            EntryIterator it = new EntryIterator(current);

            while (it.hasNext()) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }

            return modified;
        }

        public int size() {
            return TMap.this.size();
        }

        public Object[] toArray() {
            of4gwt.misc.List<Object> list = new of4gwt.misc.List<Object>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (Object o : this)
                list.add(o);

            Transaction.endRead(outer, inner);
            Object[] array = new Object[list.size()];
            list.copyToFixed(array);
            return array;
        }

        public Object[] toArray(Object[] array) {
            if (array == null)
                ExpectedExceptionThrower.throwNullPointerException();

            of4gwt.misc.List<Object> list = new of4gwt.misc.List<Object>();

            Transaction outer = Transaction.getCurrent();
            Transaction inner = Transaction.startRead(outer, TMap.this);

            for (Object o : this)
                list.add(o);

            Transaction.endRead(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class EntryIterator extends KeyedIterator implements Iterator<Entry<K, V>> {

        public EntryIterator(Transaction transaction) {
            super(transaction);
        }

        public Entry<K, V> next() {
            return nextEntry();
        }

        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKeyDirect(), current.getHash(), TKeyedEntry.REMOVAL, false);
            TMap.this.putEntry(current.getKeyDirect(), entry, false);
        }
    }

    //

    protected static class SharedVersion extends TKeyedSharedVersion {

        @Override
        public TObject.Version createVersion() {
            return new TKeyedVersion(this);
        }

        @Override
        public int getClassId() {
            return DefaultObjectModel.COM_OBJECTFABRIC_TMAP_CLASS_ID;
        }
    }
}
