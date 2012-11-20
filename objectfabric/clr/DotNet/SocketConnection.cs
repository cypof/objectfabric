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
using System.Collections.Generic;
using org.objectfabric;
using System.ServiceModel.Channels;

namespace ObjectFabric
{
    class SocketConnection : Connection
    {
        readonly Socket _socket;

        internal SocketConnection(URIResolver server, SocketRemote client, Socket socket)
            : base(server, client)
        {
            _socket = socket;

            setActor(new SocketActor(this));

            Buff buff = Buff.getOrCreate();
            int position = buff.position();
            startWrite(buff);
            buff.limit(buff.position());
            buff.position(position);

            if (Debug.ENABLED)
                buff.@lock(buff.limit());

            _socket.BeginSend(buff.getByteBuffer().array(), buff.position(), buff.remaining(), SocketFlags.None, Connected, buff);

            BeginReceive();
        }

        void Connected(IAsyncResult result)
        {
            Buff buff = (Buff) result.AsyncState;
            buff.recycle();
            getActor().onStarted();
        }

        void BeginReceive()
        {
            Buff buff = Buff.getOrCreate();
            buff.position(ImmutableReader.getLargestUnsplitable());
            Exception ex = null;

            try
            {
                _socket.BeginReceive(buff.getByteBuffer().array(), ImmutableReader.getLargestUnsplitable(), buff.remaining(), SocketFlags.None, EndReceive, buff);
            }
            catch (Exception e)
            {
                ex = e;
            }

            if (ex != null)
            {
                if (Debug.ENABLED)
                    ThreadAssert.suspend(this);

                buff.recycle();
                getClient().onError(this, ex.ToString(), true);
            }
        }

        void EndReceive(IAsyncResult result)
        {
            int length = 0;
            Exception ex = null;
            Buff buff = (Buff) result.AsyncState;

            try
            {
                length = _socket.EndReceive(result);
            }
            catch (Exception e)
            {
                ex = e;
            }

            if (ex != null || length == 0) // length == 0 means disconnected (!?)
            {
                if (Debug.ENABLED)
                {
                    ThreadAssert.suspend(this);
                    buff.@lock(buff.limit());
                }

                buff.recycle();
                getClient().onError(this, Strings.DISCONNECTED, true);
            }
            else
            {
                buff.position(ImmutableReader.getLargestUnsplitable());
                buff.limit(ImmutableReader.getLargestUnsplitable() + length);

                read(buff);

                buff.recycle();
                BeginReceive();
            }
        }


        void BeginSend(bool running)
        {
            Queue buffs = write(0xffff, running);

            if (buffs != null)
            {
                Exception ex = null;

                try
                {
                    if (buffs.size() == 1)
                    {
                        Buff buff = (Buff) buffs.get(0);
                        _socket.BeginSend(buff.getByteBuffer().array(), buff.position(), buff.remaining(), SocketFlags.None, EndSend, buffs);
                    }
                    else
                    {
                        List<ArraySegment<byte>> segments = new List<ArraySegment<byte>>(buffs.size());

                        for (int i = 0; i < buffs.size(); i++)
                        {
                            Buff buff = (Buff) buffs.get(i);
                            segments.Add(new ArraySegment<byte>(buff.getByteBuffer().array(), buff.position(), buff.remaining()));
                        }

                        _socket.BeginSend(segments, SocketFlags.None, EndSend, buffs);
                    }
                }
                catch (Exception e)
                {
                    ex = e;
                }

                if (ex != null)
                    getClient().onError(this, ex.ToString(), true);
            }
        }

        void EndSend(IAsyncResult result)
        {
            int written = 0;
            Exception ex = null;

            try
            {
                written = _socket.EndSend(result);
            }
            catch (Exception e)
            {
                ex = e;
            }

            if (ex != null)
            {
                getClient().onError(this, Strings.DISCONNECTED, true);
                return;
            }

            if (Stats.ENABLED)
                Stats.getInstance().SocketWritten.addAndGet(written);

            Queue buffs = (Queue) result.AsyncState;

            while (written > 0)
            {
                Buff buff = (Buff) buffs.peek();

                if (buff == null)
                    break;

                if (buff.remaining() > written)
                {
                    buff.position(buff.position() + written);
                    break;
                }

                written -= buff.remaining();
                buffs.poll();
                buff.recycle();
            }

            if (Debug.ENABLED)
                Debug.assertion(written >= 0);

            BeginSend(true);
        }

        internal override void close_()
        {
            base.close_();

            _socket.Close();
        }

        class SocketActor : ConnectionActor
        {
            readonly SocketConnection _connection;

            internal SocketActor(SocketConnection connection)
                : base(connection)
            {
                _connection = connection;
            }

            protected internal override void execute()
            {
                _connection.BeginSend(false);
            }
        }
    }
}