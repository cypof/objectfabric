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
using System.Net.WebSockets;
using System.Threading;
using System.Threading.Tasks;

namespace ObjectFabric
{
    class WebSocketConnection : Connection
    {
        readonly ClientWebSocket _socket;

        internal WebSocketConnection(Remote remote)
            : base(remote, null)
        {
            _socket = new ClientWebSocket();
        }

        protected override Task ConnectAsync(org.objectfabric.Address address, CancellationToken token)
        {
            return _socket.ConnectAsync(new Uri(Location.Address.ToString()), token);
        }

        protected override void Close()
        {
            _socket.CloseAsync(WebSocketCloseStatus.NormalClosure, string.Empty, CancellationToken.None);
        }

        protected override async Task<int> ReceiveAsync(ArraySegment<byte> buffer)
        {
            WebSocketReceiveResult result = await _socket.ReceiveAsync(buffer, CancellationToken.None);

            if (result.MessageType == WebSocketMessageType.Close)
                await _socket.CloseAsync(WebSocketCloseStatus.NormalClosure, string.Empty, CancellationToken.None);

            return result.Count;
        }

        protected override void Write()
        {
            org.objectfabric.Queue buffs = Fill(0xffff);

            if (buffs != null)
                SendOne(buffs);
        }

        // No array gathering support so one by one
        private void SendOne(org.objectfabric.Queue buffs)
        {
            org.objectfabric.CLRBuff buff = (org.objectfabric.CLRBuff) buffs.poll();

            if (buff == null)
                WriteComplete();
            else
            {
                try
                {
                    ArraySegment<byte> buffer = new ArraySegment<byte>(buff.array(), buff.position(), buff.limit() - buff.position());
                    Task task = _socket.SendAsync(buffer, WebSocketMessageType.Binary, false, CancellationToken.None);

                    task.ContinueWith(_ =>
                    {
                        Recycle(buff);
                        SendOne(buffs);
                    });
                }
                catch (Exception ex)
                {
                    Recycle(buff);
                    OnError(ex);
                }
            }
        }
    }
}