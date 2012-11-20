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
    public class TSet<T> : org.objectfabric.TSet, ISet<T>, INotifyCollectionChanged, INotifyPropertyChanged, TObject
    {
        static readonly TType ELEMENT_TYPE = TType.From(typeof(T));

        new public static readonly TType TYPE = new TType(DefaultObjectModel.Instance, BuiltInClass.TSET_CLASS_ID, ELEMENT_TYPE);

        public TSet(Resource resource)
            : base(resource, ELEMENT_TYPE)
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

        /// <summary>
        /// If this method is not called in the context of a transaction, the return value will always be false.
        /// Otherwise, a read is performed to return the previous value. Reads can cause a transaction to abort,
        /// so use <code>AddOnly</code> if you do not need the return value.
        /// </summary>
        public bool Add(T item)
        {
            if (item == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return add(item);
        }

        /// <summary>
        /// Does not return a value to avoid a potentially conflicting read.
        /// </summary>
        public void AddOnly(T item)
        {
            if (item == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            addOnly(item);
        }

        void ICollection<T>.Add(T item)
        {
            add(item);
        }

        public void Clear()
        {
            clear();
        }

        public bool Contains(T item)
        {
            if (item == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return contains(item);
        }

        public void CopyTo(T[] array, int index)
        {
            java.util.Iterator it = iterator();
            int i = index;

            while (it.hasNext())
                array[i++] = (T) it.next();
        }

        public void CopyTo(Array array, int index)
        {
            java.util.Iterator it = iterator();
            int i = index;

            while (it.hasNext())
                array.SetValue((object) it.next(), i++);
        }

        public int Count
        {
            get { return size(); }
        }

        IEnumerator<T> IEnumerable<T>.GetEnumerator()
        {
            java.util.Iterator it = iterator();

            while (it.hasNext())
                yield return (T) it.next();
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            java.util.Iterator it = iterator();

            while (it.hasNext())
                yield return it.next();
        }

        public void ExceptWith(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);

            Transaction outer = current_();
            Transaction inner = startWrite_(outer);
            bool ok = false;
            IteratorImpl it = new IteratorImpl(this, inner);

            try
            {
                while (it.hasNext())
                    if (set.Contains((T) it.next()))
                        it.remove();

                ok = true;
            }
            finally
            {
                endWrite_(outer, inner, ok);
            }
        }

        public void IntersectWith(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);

            Transaction outer = current_();
            Transaction inner = startWrite_(outer);
            bool ok = false;
            IteratorImpl it = new IteratorImpl(this, inner);

            try
            {
                while (it.hasNext())
                    if (!set.Contains((T) it.next()))
                        it.remove();

                ok = true;
            }
            finally
            {
                endWrite_(outer, inner, ok);
            }
        }

        public bool IsProperSubsetOf(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);
            Transaction outer = current_();
            Transaction inner = startRead_(outer);

            try
            {
                foreach (T t in this)
                    if (!set.Contains(t))
                        return false;

                return Count < set.Count;
            }
            finally
            {
                endRead_(outer, inner);
            }
        }

        public bool IsProperSupersetOf(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);
            Transaction outer = current_();
            Transaction inner = startRead_(outer);

            try
            {
                foreach (T t in set)
                    if (!Contains(t))
                        return false;

                return Count < set.Count;
            }
            finally
            {
                endRead_(outer, inner);
            }
        }

        public bool IsSubsetOf(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);
            Transaction outer = current_();
            Transaction inner = startRead_(outer);

            try
            {
                foreach (T t in this)
                    if (!set.Contains(t))
                        return false;

                return true;
            }
            finally
            {
                endRead_(outer, inner);
            }
        }

        public bool IsSupersetOf(IEnumerable<T> other)
        {
            Transaction outer = current_();
            Transaction inner = startRead_(outer);

            try
            {
                foreach (T t in other)
                    if (!Contains(t))
                        return false;

                return true;
            }
            finally
            {
                endRead_(outer, inner);
            }
        }

        public bool Overlaps(IEnumerable<T> other)
        {
            foreach (T t in other)
                if (Contains(t))
                    return true;

            return false;
        }

        public bool SetEquals(IEnumerable<T> other)
        {
            HashSet<T> set = new HashSet<T>(other);
            Transaction outer = current_();
            Transaction inner = startRead_(outer);

            try
            {
                if (Count != set.Count)
                    return false;

                foreach (T t in this)
                    if (!set.Contains(t))
                        return false;

                return true;
            }
            finally
            {
                endRead_(outer, inner);
            }
        }

        public void SymmetricExceptWith(IEnumerable<T> other)
        {
            foreach (T t in other)
                if (!this.Remove(t))
                    Add(t);
        }

        public void UnionWith(IEnumerable<T> other)
        {
            foreach (T t in other)
                Add(t);
        }

        public bool IsReadOnly
        {
            get { return false; }
        }

        public bool IsSynchronized
        {
            get { return false; }
        }

        public bool Remove(T item)
        {
            if (item == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return remove(item);
        }

        public bool Remove(object item)
        {
            if (item == null)
                throw new ArgumentNullException(Strings.ARGUMENT_NULL);

            return remove(item);
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
