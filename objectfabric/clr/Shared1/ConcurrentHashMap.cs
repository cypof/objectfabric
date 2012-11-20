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
using System.Collections.Concurrent;
using System.Collections.Generic;

namespace ObjectFabric
{
    public class ConcurrentHashMap
    {
        // Cannot compile when class derives from this
        private readonly ConcurrentDictionary<object, object> _impl = new ConcurrentDictionary<object, object>();

        protected void Clear()
        {
            _impl.Clear();
        }

        protected bool ContainsKey(object key)
        {
            return _impl.ContainsKey(key);
        }

        protected java.lang.Iterable EntrySet()
        {
            return new EntrySet(_impl);
        }

        protected object Get(object key)
        {
            object value;

            if (_impl.TryGetValue(key, out value))
                return value;

            return null;
        }

        protected bool IsEmpty()
        {
            return _impl.IsEmpty;
        }

        public java.lang.Iterable KeySet()
        {
            return new EnumerableIterable(_impl.Keys);
        }

        protected object Put(object key, object value)
        {
            object previous = null;

            _impl.AddOrUpdate(key, value,
                (Func<object, object, object>) delegate(object oldKey, object oldValue)
                {
                    previous = oldValue;
                    return value;
                });

            return previous;
        }

        protected object PutIfAbsent(object key, object value)
        {
            object previous = null;
            _impl.AddOrUpdate(key, value, (oldKey, oldValue) => previous = oldValue);
            return previous;
        }

        protected object Remove(object key)
        {
            object value;
            _impl.TryRemove(key, out value);
            return value;
        }

        protected bool Remove(object key, object expect)
        {
            return ((ICollection<KeyValuePair<object, object>>) _impl).Remove(new KeyValuePair<object, object>(key, expect));
        }

        protected bool Replace(object key, object expect, object update)
        {
            return _impl.TryUpdate(key, update, expect);
        }

        protected int Size()
        {
            return _impl.Count;
        }

        public java.lang.Iterable Values()
        {
            return new EnumerableIterable(_impl.Values);
        }
    }
}
