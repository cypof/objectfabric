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
using System.Threading.Tasks;
using System.Diagnostics;
using org.objectfabric;

namespace ObjectFabric
{
    class SocketURIHandler : ClientURIHandler
    {
        // TODO SSL support

        public static readonly SocketURIHandler Instance = new SocketURIHandler();

        private SocketURIHandler()
        {
        }

        public override URI handle( Address address, string path )
        {
            if( address.Host != null && address.Host.Length > 0 )
            {
                if( address.Scheme == Remote.TCP )
                {
                    Remote remote = get( address );
                    return URI.get( remote, path );
                }
            }

            return null;
        }

        protected internal override Remote createRemote( Address address )
        {
            return new SocketRemote( address );
        }
    }
}