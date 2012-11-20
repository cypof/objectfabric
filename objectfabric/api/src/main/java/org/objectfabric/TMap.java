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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Transactional map. For each thread this class behaves like an HashMap, except: <br>
 * <br>
 * - It does not support null keys. <br>
 * - It does not implement clone. <br>
 * - Entries do not support setValue(V). <br>
 * <br>
 * See comments on {@link TSet} about iterators and concurrency, methods that both read
 * and write, and exceptions.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TMap<K, V> extends TKeyed<K> implements Map<K, V> {

    public static final TType TYPE;

    static {
        TYPE = Platform.newTType(Platform.get().defaultObjectModel(), BuiltInClass.TMAP_CLASS_ID);
    }

    private final TType[] _genericParameters;

    public TMap(Resource resource) {
        this(resource, null, null);
    }

    /**
     * This constructor is only useful if the object might get replicated to a .NET
     * process, to specify which type would be instantiated by the remote runtime.
     */
    public TMap(Resource resource, TType genericParamKey, TType genericParamValue) {
        super(resource, new TKeyedSharedVersion());

        if (genericParamKey == null || genericParamValue == null)
            _genericParameters = null;
        else {
            _genericParameters = Platform.newTTypeArray(2);
            _genericParameters[0] = genericParamKey;
            _genericParameters[1] = genericParamValue;
        }
    }

    @Override
    final TType[] genericParameters() {
        return _genericParameters;
    }

    @Override
    public void clear() {
        clearTKeyed();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(key);
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (K) key, hash);
        } finally {
            endRead_(outer, inner);
        }

        return entry != null && !entry.isRemoval();
    }

    @Override
    public boolean containsValue(Object value) {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
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
            endRead_(outer, inner);
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

        Transaction outer = current_();
        Transaction inner = startRead_(outer);
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
            endRead_(outer, inner);
        }

        if (map.size() != size)
            return false;

        return true;
    }

    @Override
    public V get(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        int hash = hash(key);
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        TKeyedEntry entry;

        try {
            entry = getEntry(inner, (K) key, hash);
        } finally {
            endRead_(outer, inner);
        }

        return entry != null && !entry.isRemoval() ? (V) entry.getValue() : null;
    }

    @Override
    public int hashCode() {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        KeyedIterator iterator = new KeyedIterator(inner);
        int h = 0;

        try {
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.nextEntry();
                h += entry.hashCode();
            }
        } finally {
            endRead_(outer, inner);
        }

        return h;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public V put(K key, V value) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), value);
        TKeyedEntry<K, V> previous = putEntry(entry, true);
        return previous != null && !previous.isRemoval() ? previous.getValue() : null;
    }

    /**
     * Does not return a value to avoid a potentially conflicting read. This might improve
     * performance in the context of a transaction.
     */
    public void putOnly(K key, V value) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry<K, V>(key, hash(key), value);
        putEntry(entry, false);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        boolean ok = false;

        try {
            for (Map.Entry<? extends K, ? extends V> entry : t.entrySet()) {
                if (entry.getKey() == null)
                    throw new NullPointerException(Strings.ARGUMENT_NULL);

                K key = entry.getKey();
                putEntry(inner, new TKeyedEntry(key, hash(key), entry.getValue()), false);
            }

            ok = true;
        } finally {
            endWrite_(outer, inner, ok);
        }
    }

    @Override
    public V remove(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), TKeyedEntry.REMOVAL);
        TKeyedEntry<K, V> previous = putEntry(entry, true);
        return previous != null && !previous.isRemoval() ? previous.getValue() : null;
    }

    /**
     * Does not return a value to avoid a potentially conflicting read. This might improve
     * performance in the context of a transaction.
     */
    public void removeOnly(Object key) {
        if (key == null)
            throw new NullPointerException(Strings.ARGUMENT_NULL);

        TKeyedEntry entry = new TKeyedEntry(key, hash(key), TKeyedEntry.REMOVAL);
        putEntry(entry, false);
    }

    //

    @Override
    public int size() {
        return sizeTKeyed();
    }

    // Views

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    private final class KeySet implements Set<K> {

        @Override
        public boolean add(K e) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends K> c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);
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
                endRead_(outer, inner);
            }

            return result;
        }

        @Override
        public void clear() {
            TMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return TMap.this.containsKey(o);
        }

        @Override
        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        @Override
        public Iterator<K> iterator() {
            Transaction transaction = current_();
            return new KeyIterator(transaction);
        }

        @Override
        public boolean remove(Object o) {
            if (o == null)
                throw new NullPointerException(Strings.ARGUMENT_NULL);

            TKeyedEntry entry = new TKeyedEntry(o, hash(o), TKeyedEntry.REMOVAL);
            TKeyedEntry<K, V> previous = TMap.this.putEntry(entry, true);
            return previous != null && !previous.isRemoval();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
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
                endWrite_(outer, inner, ok);
            }

            return modified;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
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
                endWrite_(outer, inner, ok);
            }

            return modified;
        }

        @Override
        public int size() {
            return TMap.this.size();
        }

        @Override
        public Object[] toArray() {
            org.objectfabric.List<Object> list = new org.objectfabric.List<Object>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (K k : this)
                list.add(k);

            endRead_(outer, inner);
            Object[] array = new Object[list.size()];
            list.copyToFixed(array);
            return array;
        }

        @Override
        public <T> T[] toArray(T[] array) {
            if (array == null)
                ExpectedExceptionThrower.throwNullPointerException();

            org.objectfabric.List<T> list = new org.objectfabric.List<T>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (K k : this)
                list.add((T) k);

            endRead_(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class KeyIterator extends KeyedIterator implements Iterator<K> {

        public KeyIterator(Transaction transaction) {
            super(transaction);
        }

        @Override
        public K next() {
            return (K) nextEntry().getKey();
        }

        @Override
        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKey(), current.getHash(), TKeyedEntry.REMOVAL);
            TMap.this.putEntry(entry, false);
        }
    }

    @Override
    public Collection<V> values() {
        return new Values();
    }

    private final class Values implements Collection<V> {

        @Override
        public boolean add(V e) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public void clear() {
            TMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            return TMap.this.containsValue(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);
            boolean result = true;

            try {
                for (Object element : c) {
                    if (!TMap.this.containsValue(element)) {
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
        public Iterator<V> iterator() {
            Transaction transaction = current_();
            return new ValueIterator(transaction);
        }

        @Override
        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        @Override
        public boolean remove(Object o) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
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
                endWrite_(outer, inner, ok);
            }

            return result;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
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
                endWrite_(outer, inner, ok);
            }

            return modified;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
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
                endWrite_(outer, inner, ok);
            }

            return modified;
        }

        @Override
        public int size() {
            return TMap.this.size();
        }

        @Override
        public Object[] toArray() {
            org.objectfabric.List<Object> list = new org.objectfabric.List<Object>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (V e : this)
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

            org.objectfabric.List<T> list = new org.objectfabric.List<T>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (V e : this)
                list.add((T) e);

            endRead_(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class ValueIterator extends KeyedIterator implements Iterator<V> {

        public ValueIterator(Transaction transaction) {
            super(transaction);
        }

        @Override
        public V next() {
            return (V) nextEntry().getValue();
        }

        @Override
        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKey(), current.getHash(), TKeyedEntry.REMOVAL);
            TMap.this.putEntry(entry, false);
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet implements Set {

        @Override
        public boolean add(Object o) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public boolean addAll(Collection c) {
            ExpectedExceptionThrower.throwUnsupportedOperationException();
            return false;
        }

        @Override
        public void clear() {
            TMap.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;

            Entry<K, V> given = (Entry<K, V>) o;
            K key = given.getKey();
            int hash = hash(key);

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);
            TKeyedEntry stored;

            try {
                stored = getEntry(inner, key, hash);
            } finally {
                endRead_(outer, inner);
            }

            return stored != null && stored.equals(given);
        }

        @Override
        public boolean containsAll(Collection c) {
            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);
            boolean result = true;

            try {
                for (Object element : c) {
                    if (!contains(element)) {
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
        public boolean isEmpty() {
            return TMap.this.isEmpty();
        }

        @Override
        public Iterator iterator() {
            Transaction transaction = current_();
            return new EntryIterator(transaction);
        }

        @Override
        public boolean remove(Object o) {
            if (o == null)
                throw new NullPointerException(Strings.ARGUMENT_NULL);

            if (!(o instanceof Entry))
                return false;

            Entry<K, V> given = (Entry<K, V>) o;
            K key = given.getKey();
            int hash = hash(key);

            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
            boolean modified = false, ok = false;

            try {
                TKeyedEntry stored = getEntry(inner, key, hash);

                if (stored != null && stored.equals(given)) {
                    TKeyedEntry entry = new TKeyedEntry(key, hash, TKeyedEntry.REMOVAL);
                    putEntry(inner, entry, true);
                    modified = true;
                }

                ok = true;
            } finally {
                endWrite_(outer, inner, ok);
            }

            return modified;
        }

        @Override
        public boolean removeAll(Collection c) {
            if (c == null)
                throw new NullPointerException();

            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
            boolean modified = false, ok = false;

            try {
                EntryIterator it = new EntryIterator(inner);

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
        public boolean retainAll(Collection c) {
            if (c == null)
                throw new NullPointerException();

            Transaction outer = current_();
            Transaction inner = TMap.this.startWrite_(outer);
            boolean modified = false, ok = false;

            try {
                EntryIterator it = new EntryIterator(inner);

                while (it.hasNext()) {
                    if (!c.contains(it.next())) {
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
        public int size() {
            return TMap.this.size();
        }

        @Override
        public Object[] toArray() {
            org.objectfabric.List<Object> list = new org.objectfabric.List<Object>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (Object o : this)
                list.add(o);

            endRead_(outer, inner);
            Object[] array = new Object[list.size()];
            list.copyToFixed(array);
            return array;
        }

        @Override
        public Object[] toArray(Object[] array) {
            if (array == null)
                ExpectedExceptionThrower.throwNullPointerException();

            org.objectfabric.List<Object> list = new org.objectfabric.List<Object>();

            Transaction outer = current_();
            Transaction inner = TMap.this.startRead_(outer);

            for (Object o : this)
                list.add(o);

            endRead_(outer, inner);
            return list.copyToWithResizeAndNullEnd(array);
        }
    }

    private final class EntryIterator extends KeyedIterator implements Iterator<Entry<K, V>> {

        public EntryIterator(Transaction transaction) {
            super(transaction);
        }

        @Override
        public Entry<K, V> next() {
            return nextEntry();
        }

        @Override
        public void remove() {
            TKeyedEntry current = getCurrent();
            TKeyedEntry entry = new TKeyedEntry(current.getKey(), current.getHash(), TKeyedEntry.REMOVAL);
            TMap.this.putEntry(entry, false);
        }
    }

    //

    @Override
    protected final int classId_() {
        return BuiltInClass.TMAP_CLASS_ID;
    }
}
