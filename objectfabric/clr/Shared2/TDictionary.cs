///
/// This file is part of ObjectFabric (http://objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// Copyright ObjectFabric Inc.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
///

using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using org.objectfabric;

namespace ObjectFabric
{
    public class TDictionary<K, V> : TMap, IDictionary<K, V>, IDictionary, INotifyCollectionChanged, INotifyPropertyChanged, TObject
    {
        static readonly TType KEY_TYPE = TType.From(typeof(K)), VALUE_TYPE = TType.From(typeof(V));

        new public static readonly TType TYPE = new TType(DefaultObjectModel.Instance, BuiltInClass.TMAP_CLASS_ID, KEY_TYPE, VALUE_TYPE);

        public TDictionary(Resource resource)
            : base(resource, KEY_TYPE, VALUE_TYPE)
        {
        }

        public Resource Resource
        {
            get { return (Resource) resource(); }
        }

        public Workspace Workspace
        {
            get { return (Workspace) workspace(); }
        }

        public void Add(K key, V value)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            if (containsKey(key))
                throw new ArgumentException("Key already exists in dictionary");

            put(key, value);
        }

        void IDictionary.Add(object key, object value)
        {
            Add((K) key, (V) value);
        }

        public void Add(KeyValuePair<K, V> item)
        {
            Add(item.Key, item.Value);
        }

        public void Clear()
        {
            clear();
        }

        public bool ContainsKey(K key)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return containsKey(key);
        }

        bool IDictionary.Contains(object key)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return containsKey(key);
        }

        public bool Contains(KeyValuePair<K, V> item)
        {
            throw new NotImplementedException();
        }

        public void CopyTo(Array array, int index)
        {
            int i = 0;

            foreach (KeyValuePair<K, V> entry in this)
                array.SetValue(entry, i++);
        }

        public void CopyTo(KeyValuePair<K, V>[] array, int arrayIndex)
        {
            int i = 0;

            foreach (KeyValuePair<K, V> entry in this)
                array[i++] = entry;
        }

        public int Count
        {
            get { return size(); }
        }

        public IEnumerator<KeyValuePair<K, V>> GetEnumerator()
        {
            java.util.Iterator it = entrySet().iterator();

            while (it.hasNext())
            {
                java.util.Map.Entry entry = (java.util.Map.Entry) it.next();
                yield return new KeyValuePair<K, V>((K) entry.getKey(), (V) entry.getValue());
            }
        }

        IDictionaryEnumerator IDictionary.GetEnumerator()
        {
            throw new NotSupportedException();
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            return GetEnumerator();
        }

        bool IDictionary.IsFixedSize
        {
            get { return false; }
        }

        public bool IsReadOnly
        {
            get { return false; }
        }

        public bool IsSynchronized
        {
            get { return false; }
        }

        public ICollection<K> Keys
        {
            get { return new CollectionWrapper<K>(keySet()); }
        }

        ICollection IDictionary.Keys
        {
            get { return new CollectionWrapper<K>(keySet()); }
        }

        public bool Remove(K key)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            bool contained = containsKey(key);
            remove(key);
            return contained;
        }

        public bool Remove(KeyValuePair<K, V> item)
        {
            int hash = TKeyed.hash(item.Key);
            Transaction outer = current_();
            Transaction inner = startWrite_(outer);
            bool ok = false, result = false;

            try
            {
                TKeyedEntry entry = getEntry(inner, (K) item.Key, hash);

                if (item.Equals(new KeyValuePair<K, V>((K) entry.getKey(), (V) entry.getValue())))
                {
                    remove(entry.getKey());
                    result = true;
                }

                ok = true;
            }
            finally
            {
                endWrite_(outer, inner, ok);
            }

            return result;
        }

        void IDictionary.Remove(object key)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            remove(key);
        }

        public object SyncRoot
        {
            get { return this; }
        }

        public V this[K key]
        {
            get
            {
                if (key == null)
                    throw new ArgumentNullException(Strings.ARGUMENT_NULL);

                if (!containsKey(key))
                    throw new KeyNotFoundException();

                return (V) get(key);
            }
            set { put(key, value); }
        }

        object IDictionary.this[object key]
        {
            get
            {
                if (key == null)
                    throw new ArgumentNullException(Strings.ARGUMENT_NULL);

                if (!containsKey(key))
                    throw new KeyNotFoundException();

                return get(key);
            }
            set { put(key, value); }
        }

        public bool TryGetValue(K key, out V value)
        {
            if (key == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            value = (V) get(key);
            return containsKey(key);
        }

        new public ICollection<V> Values
        {
            get { return new CollectionWrapper<V>(values()); }
        }

        ICollection IDictionary.Values
        {
            get { return new CollectionWrapper<V>(values()); }
        }

        // Override Java behavior for equality & Hash which uses content.

        public override bool equals(object obj)
        {
            return this == obj;
        }

        public override int hashCode()
        {
            return RuntimeHelpers.GetHashCode(this);
        }

        // Events

        public event Action<K> Added
        {
            add { addListener(new AddedListener(value)); }
            remove { removeListener(new AddedListener(value)); }
        }

        class AddedListener : Listener<Action<K>>, org.objectfabric.KeyListener
        {
            public AddedListener(Action<K> d)
                : base(d)
            {
            }

            public void onPut(object obj)
            {
                _delegate((K) obj);
            }

            public void onRemove(object obj) { }

            public void onClear() { }
        }

        //

        public event Action<K> Removed
        {
            add { addListener(new RemovedListener(value)); }
            remove { removeListener(new RemovedListener(value)); }
        }

        class RemovedListener : Listener<Action<K>>, org.objectfabric.KeyListener
        {
            public RemovedListener(Action<K> d)
                : base(d)
            {
            }

            public void onPut(object obj) { }

            public void onRemove(object obj)
            {
                _delegate((K) obj);
            }

            public void onClear() { }
        }

        //

        public event Action Cleared
        {
            add { addListener(new ClearedListener(value)); }
            remove { removeListener(new ClearedListener(value)); }
        }

        class ClearedListener : Listener<Action>, org.objectfabric.KeyListener
        {
            public ClearedListener(Action d)
                : base(d)
            {
            }

            public void onPut(object obj) { }

            public void onRemove(object obj) { }

            public void onClear()
            {
                _delegate();
            }
        }

        //

        public event PropertyChangedEventHandler PropertyChanged
        {
            add { addListener(new DictionaryCountListener(this, value)); }
            remove { removeListener(new DictionaryCountListener(this, value)); }
        }

        public event NotifyCollectionChangedEventHandler CollectionChanged
        {
            add { addListener(new DictionaryListener(this, value)); }
            remove { removeListener(new DictionaryListener(this, value)); }
        }
    }
}
