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

using System.Collections;
using System.Collections.Generic;

namespace ObjectFabric
{
    public abstract class CLRList : IEnumerable
    {
        // Cannot compile when class derives from this
        private readonly List<object> _impl = new List<object>();

        public void Add(object o)
        {
            _impl.Add(o);
        }

        public void Clear()
        {
            _impl.Clear();
        }

        public int Count
        {
            get { return _impl.Count; }
        }

        public object Get(int index)
        {
            return _impl[index];
        }

        public IEnumerator GetEnumerator()
        {
            return _impl.GetEnumerator();
        }

        public void Remove(int index)
        {
            _impl.Remove(index);
        }
    }
}
