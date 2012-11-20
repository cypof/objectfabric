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
    public abstract class CLRSet : IEnumerable
    {
        // Cannot compile when class derives from this
        private readonly HashSet<object> _impl = new HashSet<object>();

        public bool Add(object element)
        {
            return _impl.Add(element);
        }

        public bool Contains(object element)
        {
            return _impl.Contains(element);
        }

        public bool Remove(object element)
        {
            return _impl.Remove(element);
        }

        public int Count
        {
            get { return _impl.Count; }
        }

        public IEnumerator GetEnumerator()
        {
            return _impl.GetEnumerator();
        }
    }
}
