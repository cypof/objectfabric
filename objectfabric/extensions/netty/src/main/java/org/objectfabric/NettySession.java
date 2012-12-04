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
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;

/**
 * To be added to a socket or WebSocket server pipeline.
 */
public class NettySession extends SimpleChannelUpstreamHandler {

    private final Server _server;

    private final WebSocketServerHandshakerFactory _wsFactory;

    private WebSocketServerHandshaker _handshaker;

    private NettyConnection _connection;

    public NettySession(Server server) {
        this(server, null);
    }

    public NettySession(Server server, WebSocketServerHandshakerFactory wsFactory) {
        _server = server;
        _wsFactory = wsFactory;
    }

    final Connection getConnection() {
        return _connection;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();

        if (msg instanceof ChannelBuffer) {
            if (_connection == null)
                _connection = createConnection(ctx.getChannel(), false, null);

            _connection.read((ChannelBuffer) msg);
        } else if (msg instanceof HttpRequest)
            handleHttpRequest(ctx, (HttpRequest) msg);
        else if (msg instanceof WebSocketFrame)
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
    }

    protected void handleHttpRequest(ChannelHandlerContext ctx, final HttpRequest req) throws Exception {
        WebSocketServerHandshakerFactory factory;

        if (_wsFactory != null)
            factory = _wsFactory;
        else {
            String location = "ws://" + req.getHeader(HttpHeaders.Names.HOST) + Remote.WS_PATH;
            factory = new WebSocketServerHandshakerFactory(location, null, false);
        }

        _handshaker = factory.newHandshaker(req);

        if (_handshaker == null)
            factory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        else {
            _handshaker.handshake(ctx.getChannel(), req).addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess())
                        _connection = createConnection(future.getChannel(), true, new NettyHeaders(req));
                    else
                        Channels.fireExceptionCaught(future.getChannel(), future.getCause());
                }
            });
        }
    }

    protected NettyConnection createConnection(org.jboss.netty.channel.Channel channel, boolean webSocket, Headers headers) {
        return new NettyConnection(_server, channel, webSocket, headers);
    }

    protected void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame) {
            _handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
            return;
        }

        if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame data = (BinaryWebSocketFrame) frame;
            _connection.read(data.getBinaryData());
            return;
        }

        if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame data = (ContinuationWebSocketFrame) frame;
            _connection.read(data.getBinaryData());
            return;
        }

        if (frame instanceof PongWebSocketFrame)
            return;

        throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (_connection != null)
            _connection.requestClose(null);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (!(e.getCause() instanceof IOException))
            if (!(e.getCause() instanceof WebSocketHandshakeException))
                if (!(e.getCause() instanceof IllegalArgumentException && e.getCause().getMessage().contains("empty text")))
                    Log.write(e.getCause());

        e.getChannel().close();
    }

    private static class NettyHeaders extends Headers {

        private final HttpRequest _req;

        NettyHeaders(HttpRequest req) {
            _req = req;
        }

        @Override
        public String get(String name) {
            return _req.getHeader(name);
        }

        @Override
        public List<String> getMultiple(String name) {
            return _req.getHeaders(name);
        }

        @Override
        public List<Entry<String, String>> getAsList() {
            return _req.getHeaders();
        }

        @Override
        public boolean contains(String name) {
            return _req.containsHeader(name);
        }

        @Override
        public Set<String> getNames() {
            return _req.getHeaderNames();
        }

        @Override
        public void add(String name, String value) {
            _req.addHeader(name, value);
        }

        @Override
        public void set(String name, String value) {
            _req.setHeader(name, value);
        }

        @Override
        public void set(String name, Iterable<String> values) {
            _req.setHeader(name, values);
        }

        @Override
        public void removeHeader(String name) {
            _req.removeHeader(name);
        }

        @Override
        public void clearHeaders() {
            _req.clearHeaders();
        }
    }
}
