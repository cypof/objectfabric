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
using System.Collections.Generic;
using System.Text;
using System.Collections;
using java.util;

namespace ObjectFabric
{
    public class ReadOnlyCollection<T> : ICollection<T>, ICollection
    {
        readonly Collection _collection;

        public ReadOnlyCollection( Collection collection )
        {
            _collection = collection;
        }

        void ICollection<T>.Add( T item )
        {
            throw new NotSupportedException();
        }

        void ICollection<T>.Clear()
        {
            throw new NotSupportedException();
        }

        public bool Contains( T item )
        {
            return _collection.contains( item );
        }

        public void CopyTo( T[] array, int arrayIndex )
        {

            java.util.Iterator it = _collection.iterator();
            int i = arrayIndex;

            while( it.hasNext() && i < array.Length )
                array[i++] = (T) it.next();
        }

        public void CopyTo( Array array, int index )
        {
            java.util.Iterator it = _collection.iterator();
            int i = index;

            while( it.hasNext() && i < array.Length )
                array.SetValue( it.next(), i++ );
        }

        public int Count
        {
            get { return _collection.size(); }
        }

        public IEnumerator<T> GetEnumerator()
        {
            java.util.Iterator it = _collection.iterator();

            while( it.hasNext() )
                yield return (T) it.next();
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            java.util.Iterator it = _collection.iterator();

            while( it.hasNext() )
                yield return it.next();
        }

        public bool IsReadOnly
        {
            get { return true; }
        }

        public bool IsSynchronized
        {
            get { return false; }
        }

        bool ICollection<T>.Remove( T item )
        {
            throw new NotSupportedException();
        }

        object ICollection.SyncRoot
        {
            get { throw new NotImplementedException(); }
        }
    }
}
