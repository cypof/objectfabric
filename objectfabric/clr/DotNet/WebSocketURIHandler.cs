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

namespace ObjectFabric
{
    /// <summary>
    /// WebSocket handler for "ws" and "wss" URIs, based on .NET 4.5 System.Net.WebSockets.
    /// </summary>
    public class WebSocketURIHandler : ClientURIHandler
    {
        public override org.objectfabric.URI handle(org.objectfabric.Address address, string path)
        {
            if (address.Host != null && address.Host.Length > 0)
            {
                if (address.Scheme == Remote.WS || address.Scheme == Remote.WSS)
                {
                    org.objectfabric.Remote remote = get(address);
                    return remote.getURI(path);
                }
            }

            return null;
        }

        protected override Remote CreateRemote(org.objectfabric.Address address)
        {
            return new WebSocketRemote(address);
        }

        class WebSocketRemote : Remote
        {
            internal WebSocketRemote(org.objectfabric.Address address)
                : base(false, address)
            {
            }

            protected override void Connect(System.Threading.CancellationToken token)
            {
                WebSocketConnection connection = new WebSocketConnection(this);
                connection.Start(token);
            }
        };
    }
}