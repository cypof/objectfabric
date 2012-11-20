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

using System.Collections.Generic;

namespace ObjectFabric
{
    public abstract class CLRMap
    {
        // Cannot compile when class derives from this
        private readonly Dictionary<object, object> _impl = new Dictionary<object, object>();

        public void Clear()
        {
            _impl.Clear();
        }

        public int Count
        {
            get { return _impl.Count; }
        }

        public bool ContainsKey(object key)
        {
            return _impl.ContainsKey(key);
        }

        protected java.lang.Iterable EntrySet()
        {
            return new EntrySet(_impl);
        }

        public object Get(object key)
        {
            object value;

            if (_impl.TryGetValue(key, out value))
                return _impl[key];

            return null;
        }

        public java.lang.Iterable Keys()
        {
            return new EnumerableIterable(_impl.Keys);
        }

        public void Put(object key, object value)
        {
            _impl[key] = value;
        }

        public void Remove(object key)
        {
            _impl.Remove(key);
        }

        public java.lang.Iterable Values()
        {
            return new EnumerableIterable(_impl.Values);
        }
    }
}
