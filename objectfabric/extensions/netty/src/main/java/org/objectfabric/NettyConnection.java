/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ByteBufferBackedChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.objectfabric.CloseCounter.Callback;

class NettyConnection extends Connection {

    private final Channel _channel;

    private final boolean _webSocket;

    protected NettyConnection(Location location, Channel channel, boolean webSocket, Headers headers) {
        super(location, headers);

        _channel = channel;
        _webSocket = webSocket;

        onStarted();
    }

    @Override
    void onClose(Callback callback) {
        super.onClose(callback);

        _channel.close();
    }

    @Override
    protected Session onConnection(Headers headers) {
        if (location() instanceof Server) {
            InetSocketAddress source = (InetSocketAddress) _channel.getRemoteAddress();
            return ((Server) location()).onConnection(source, address(), headers, _channel);
        }

        return super.onConnection(headers);
    }

    final void read(ChannelBuffer buffer) {
        if (resumeRead()) {
            // TODO wrap if already a byte buffer
            while (buffer.readableBytes() != 0) {
                JVMBuff buff = (JVMBuff) Buff.getOrCreate();

                // To put remaining from last buffer
                buff.position(Buff.getLargestUnsplitable());
                int length = Math.min(buff.remaining(), buffer.readableBytes());
                buff.limit(Buff.getLargestUnsplitable() + length);
                buffer.getBytes(buffer.readerIndex(), buff.getByteBuffer());
                buffer.readerIndex(buffer.readerIndex() + length);
                buff.position(Buff.getLargestUnsplitable());

                read(buff);

                buff.recycle();
            }

            suspendRead();
        }
    }

    @Override
    protected void write() {
        Queue<Buff> buffs = fill(0xffff);

        if (buffs != null) {
            // TODO Gather / send at once
            ChannelFuture future = null;

            for (;;) {
                final JVMBuff buff = (JVMBuff) buffs.poll();

                if (buff == null)
                    break;

                ByteBuffer defaultOrder = buff.getByteBuffer().duplicate();
                ChannelBuffer buffer = new ByteBufferBackedChannelBuffer(defaultOrder);
                Exception ex = null;

                try {
                    if (_webSocket)
                        future = _channel.write(new BinaryWebSocketFrame(buffer));
                    else
                        future = _channel.write(buffer);
                } catch (Exception e) {
                    ex = e;
                }

                if (ex != null) {
                    _channel.close();
                    return;
                }

                if (future == null || future.isDone())
                    buff.recycle();
                else {
                    if (Debug.THREADS)
                        ThreadAssert.exchangeGive(buff, buff);

                    future.addListener(new ChannelFutureListener() {

                        @Override
                        public void operationComplete(ChannelFuture _) throws Exception {
                            if (Debug.THREADS)
                                ThreadAssert.exchangeTake(buff);

                            buff.recycle();
                        }
                    });
                }
            }

            if (future == null || future.isDone())
                writeComplete();
            else {
                future.addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture _) throws Exception {
                        writeComplete();
                    }
                });
            }
        }
    }
}
