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

using System.Threading;

namespace ObjectFabric
{
    public abstract class Remote : org.objectfabric.Remote, Location
    {
        new public static readonly string TCP = org.objectfabric.Remote.TCP;

        new public static readonly string SSL = org.objectfabric.Remote.SSL;

        new public static readonly string WS = org.objectfabric.Remote.WS;

        new public static readonly string WSS = org.objectfabric.Remote.WSS;

        protected Remote(bool cache, org.objectfabric.Address address)
            : base(cache, address)
        {
        }

        public org.objectfabric.Address Address
        {
            get { return address(); }
        }

        new public ObjectFabric.Status Status
        {
            get { return (ObjectFabric.Status) status().ordinal(); }
        }

        public bool IsCache
        {
            get { return isCache(); }
        }

        protected abstract void Connect(CancellationToken token);

        internal override ConnectionAttempt createAttempt()
        {
            return new Attempt(this);
        }

        class Attempt : ConnectionAttempt
        {
            readonly Remote _remote;
            readonly CancellationTokenSource _source = new CancellationTokenSource();

            internal Attempt(Remote remote)
            {
                _remote = remote;
            }

            public void start()
            {
                _remote.Connect(_source.Token);
            }

            public void cancel()
            {
                _source.Cancel();
            }
        }

        protected virtual org.objectfabric.Headers Headers
        {
            get { return null; }
        }

        internal override org.objectfabric.Headers headers()
        {
            return Headers;
        }
    }
}
