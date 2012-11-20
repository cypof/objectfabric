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
using org.objectfabric;
using System.Net.Sockets;


namespace ObjectFabric
{
    class SocketRemote : Remote
    {
        internal SocketRemote(Address address)
            : base(false, address)
        {
        }

        internal override java.io.Closeable connectAsync()
        {
            return new Attempt(this);
        }

        class Attempt : java.io.Closeable
        {
            readonly SocketRemote _remote;

            readonly Socket _socket;

            internal Attempt(SocketRemote remote)
            {
                _remote = remote;

                _socket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
                _socket.BeginConnect(_remote.getAddress().Host, _remote.getAddress().Port, EndConnect, null);
            }

            void EndConnect(IAsyncResult result)
            {
                _socket.EndConnect(result);
                _remote.setChannel(new SocketConnection(null, _remote, _socket));
            }

            public void close()
            {
                _socket.Close();
            }

            public void Dispose() // IKVM
            {
                close();
            }
        }
    }
}