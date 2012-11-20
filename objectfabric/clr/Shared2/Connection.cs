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
using System.Threading;
using System.Threading.Tasks;

namespace ObjectFabric
{
    public abstract class Connection : org.objectfabric.Connection
    {
        protected Connection(Remote remote, org.objectfabric.Headers nativeHeaders)
            : base(remote, nativeHeaders)
        {
        }

        public Remote Location
        {
            get { return (Remote) location(); }
        }

        protected abstract Task ConnectAsync(org.objectfabric.Address address, CancellationToken token);

        public async void Start(CancellationToken token)
        {
            try
            {
                await ConnectAsync(Location.Address, token);

                Location.onConnection(this);
                onStarted();
                Receive();
            }
            catch (Exception ex)
            {
                Location.onError(this, ex.Message, true);
            }
        }

        protected abstract void Close();

        internal override void onClose(org.objectfabric.CloseCounter.Callback callback)
        {
            base.onClose(callback);

            Close();
        }

        protected abstract Task<int> ReceiveAsync(ArraySegment<byte> buffer);

        async void Receive()
        {
            org.objectfabric.CLRBuff buff = (org.objectfabric.CLRBuff) org.objectfabric.CLRBuff.getOrCreate();
            buff.position(org.objectfabric.Buff.getLargestUnsplitable());

            try
            {
                int length = await ReceiveAsync(new ArraySegment<byte>(buff.array(), buff.position(), buff.remaining()));

                if (length > 0 && resumeRead())
                {
                    buff.limit(buff.position() + length);
                    read(buff);
                    suspendRead();
                }

                Receive();
            }
            catch (Exception ex)
            {
                Location.onError(this, ex.Message, true);
            }
            finally
            {
                if (org.objectfabric.Debug.ENABLED)
                    buff.@lock(buff.limit());

                buff.recycle();
            }
        }

        new protected abstract void Write();

        internal override void write()
        {
            Write();
        }

        protected org.objectfabric.Queue Fill(int limit)
        {
            return fill(limit);
        }

        protected void Recycle(org.objectfabric.CLRBuff buff)
        {
            buff.recycle();
        }

        protected void WriteComplete()
        {
            writeComplete();
        }

        protected void OnError(Exception ex)
        {
            Location.onError(this, ex.Message, true);
        }
    }
}
