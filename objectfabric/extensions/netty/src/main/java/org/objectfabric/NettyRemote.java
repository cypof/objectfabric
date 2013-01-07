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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.channels.CancelledKeyException;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.jboss.netty.util.CharsetUtil;

class NettyRemote extends Remote {

    private final Netty _handler;

    protected NettyRemote(Address address, Netty handler) {
        super(false, address);

        _handler = handler;
    }

    @Override
    ConnectionAttempt createAttempt() {
        return new Attempt();
    }

    private final class Attempt implements ConnectionAttempt, Runnable {

        private volatile boolean _cancelled;

        private volatile ChannelFuture _connect;

        @Override
        public void start() {
            if (ClientURIHandler.isEnabled())
                ThreadPool.getInstance().execute(this);
        }

        @Override
        public void cancel() {
            _cancelled = true;

            ChannelFuture connect = _connect;

            if (connect != null)
                connect.cancel();
        }

        @Override
        public void run() {
            if (!_cancelled) {
                InetAddress host = null;

                try {
                    host = InetAddress.getByName(address().Host);
                } catch (Exception e) {
                    onError(null, Strings.URI_UNRESOLVED + address() + ", " + e, false);
                    return;
                }

                if (host != null) {
                    try {
                        _connect = connect(host);
                    } catch (Exception e) {
                        onError(null, Platform.get().getStackAsString(e), false);
                        return;
                    }
                }
            }
        }
    }

    protected ChannelFuture connect(InetAddress host) {
        ClientBootstrap bootstrap = new ClientBootstrap(_handler.getChannelFactory());
        int port = address().Port;
        boolean abort = false;
        final boolean tcp = Remote.TCP.equals(address().Scheme);
        final boolean ssl = Remote.SSL.equals(address().Scheme);
        final boolean ws = Remote.WS.equals(address().Scheme);
        final boolean wss = Remote.WSS.equals(address().Scheme);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = _handler.createPipeline(address());

                if (tcp || ssl)
                    pipeline.addLast("tcp-handler", new TCPClientHandler());

                if (ws || wss)
                    pipeline.addLast("ws-handler", new WebSocketClientHandler());

                return pipeline;
            }
        });

        if (ws) {
            if (port == Address.NULL_PORT)
                port = 80;
        } else if (wss) {
            if (port == Address.NULL_PORT)
                port = 443;
        }

        if (port == Address.NULL_PORT) {
            onError(null, Strings.URI_MISSING_PORT + address(), false);
            abort = true;
        }

        ChannelFuture future = null;

        if (!abort)
            future = bootstrap.connect(new InetSocketAddress(host, port));

        return future;
    }

    protected NettyConnection createConnection(Channel channel, boolean webSocket) {
        return new NettyConnection(this, channel, webSocket, null);
    }

    protected class TCPClientHandler extends SimpleChannelUpstreamHandler {

        NettyConnection _connection;

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            _connection = createConnection(ctx.getChannel(), false);
            onConnection(_connection);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            _connection.read((ChannelBuffer) e.getMessage());
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
            onError(_connection, Strings.DISCONNECTED, true);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            if (e.getCause() instanceof IOException)
                return;

            if (e.getCause() instanceof CancelledKeyException)
                return;

            Log.write(e.getCause());
        }
    }

    protected class WebSocketClientHandler extends TCPClientHandler {

        private final WebSocketClientHandshaker _handshaker;

        WebSocketClientHandler() {
            // HashMap<String, String> customHeaders = new HashMap<String, String>();
            // customHeaders.put("MyHeader", "MyValue");
            java.net.URI uri;

            try {
                uri = new java.net.URI(address().toString() + Remote.WS_PATH);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            _handshaker = new WebSocketClientHandshakerFactory().newHandshaker(uri, WebSocketVersion.V13, null, false, null);
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            _handshaker.handshake(ctx.getChannel());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            Channel channel = ctx.getChannel();

            if (!_handshaker.isHandshakeComplete())
                _handshaker.finishHandshake(channel, (HttpResponse) e.getMessage());
            else {
                if (_connection == null) {
                    _connection = createConnection(ctx.getChannel(), true);
                    onConnection(_connection);
                }

                if (e.getMessage() instanceof HttpResponse) {
                    HttpResponse response = (HttpResponse) e.getMessage();
                    throw new Exception("Unexpected HttpResponse (status=" + response.getStatus() + ", content=" + response.getContent().toString(CharsetUtil.UTF_8) + ")");
                }

                WebSocketFrame frame = (WebSocketFrame) e.getMessage();

                if (frame instanceof BinaryWebSocketFrame) {
                    BinaryWebSocketFrame data = (BinaryWebSocketFrame) frame;
                    _connection.read(data.getBinaryData());
                } else if (frame instanceof CloseWebSocketFrame)
                    channel.close();
                else
                    Debug.failAlways();
            }
        }
    }

    @Override
    Headers headers() {
        return _handler.getHeaders(address());
    }
}