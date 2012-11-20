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

using System.Net.Sockets;

namespace JavaTests
{
    class DistributedSerializationTest : org.objectfabric.SerializationTest
    {
        readonly Socket _socket = new Socket( AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp );

        public DistributedSerializationTest()
        {
            _socket.Connect( "localhost", 4444 );
        }

        protected internal override byte[] exchange( byte[] buffer )
        {
            _socket.Send( buffer );
            _socket.Receive( buffer );
            return buffer;
        }
    }
}
