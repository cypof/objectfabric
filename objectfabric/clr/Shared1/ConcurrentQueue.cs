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

using System.Collections.Concurrent;
using System.Collections;

namespace ObjectFabric
{
    public class ConcurrentQueue : IEnumerable
    {
        private readonly ConcurrentQueue<object> _impl = new ConcurrentQueue<object>();

        protected void Enqueue(object item)
        {
            _impl.Enqueue(item);
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            return _impl.GetEnumerator();
        }

        protected bool IsEmpty()
        {
            return _impl.IsEmpty;
        }

        protected object Peek()
        {
            object result;

            if (_impl.TryPeek(out result))
                return result;

            return null;
        }

        protected object Poll()
        {
            object result;

            if (_impl.TryDequeue(out result))
                return result;

            return null;
        }

        protected int Size()
        {
            return _impl.Count;
        }
    }
}
