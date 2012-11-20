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

namespace ObjectFabric
{
    public class EnumeratorIterator : java.util.Iterator
    {
        private readonly IEnumerator _enumerator;

        public EnumeratorIterator(IEnumerator enumerator)
        {
            this._enumerator = enumerator;
        }

        public bool hasNext()
        {
            return _enumerator.MoveNext();
        }

        public object next()
        {
            return _enumerator.Current;
        }

        public void remove()
        {
            throw new NotImplementedException();
        }
    }

    public class EnumerableIterable : java.lang.Iterable
    {
        private readonly IEnumerable _enumerable;

        public EnumerableIterable(IEnumerable enumerable)
        {
            _enumerable = enumerable;
        }

        public java.util.Iterator iterator()
        {
            return new EnumeratorIterator(_enumerable.GetEnumerator());
        }
    }

    class EntrySet : java.lang.Iterable
    {
        private readonly IEnumerable<KeyValuePair<object, object>> _enumerable;

        public EntrySet(IEnumerable<KeyValuePair<object, object>> enumerable)
        {
            _enumerable = enumerable;
        }

        public java.util.Iterator iterator()
        {
            return new EntryIterator(_enumerable.GetEnumerator());
        }

        private class EntryIterator : java.util.Iterator
        {
            private readonly IEnumerator<KeyValuePair<object, object>> _enumerator;

            public EntryIterator(IEnumerator<KeyValuePair<object, object>> enumerator)
            {
                this._enumerator = enumerator;
            }

            public bool hasNext()
            {
                return _enumerator.MoveNext();
            }

            public object next()
            {
                return new Entry(_enumerator.Current);
            }

            public void remove()
            {
                throw new NotImplementedException();
            }

            private struct Entry : java.util.Map.Entry
            {
                KeyValuePair<object, object> _pair;

                public Entry(KeyValuePair<object, object> pair)
                {
                    _pair = pair;
                }

                public object getKey()
                {
                    return _pair.Key;
                }

                public object getValue()
                {
                    return _pair.Value;
                }

                public object setValue(object obj)
                {
                    throw new NotImplementedException();
                }

                public bool equals(object o)
                {
                    return Equals(o);
                }

                public int hashCode()
                {
                    return GetHashCode();
                }
            }
        }
    }
}
