///**
// * This file is part of ObjectFabric (http://objectfabric.org).
// *
// * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
// * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
// * 
// * Copyright ObjectFabric Inc.
// * 
// * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
// * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
// */
//
//package org.objectfabric;
//
//import java.io.IOException;
//import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.Executors;
//
//import org.jboss.netty.bootstrap.ServerBootstrap;
//import org.jboss.netty.buffer.ChannelBuffer;
//import org.jboss.netty.channel.ChannelFuture;
//import org.jboss.netty.channel.ChannelFutureListener;
//import org.jboss.netty.channel.ChannelHandlerContext;
//import org.jboss.netty.channel.ChannelPipeline;
//import org.jboss.netty.channel.ChannelPipelineFactory;
//import org.jboss.netty.channel.ChannelStateEvent;
//import org.jboss.netty.channel.Channels;
//import org.jboss.netty.channel.ExceptionEvent;
//import org.jboss.netty.channel.MessageEvent;
//import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
//import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
//import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
//import org.jboss.netty.handler.codec.http.HttpHeaders;
//import org.jboss.netty.handler.codec.http.HttpRequest;
//import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
//import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
//import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
//import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
//import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
//import org.objectfabric.Queue;
//
//public class DistributedSerializationTest extends SerializationTestJava {
//
//    public class NettySession extends SimpleChannelUpstreamHandler {
//
//        private final ConcurrentLinkedQueue<byte[]> _reads = new ConcurrentLinkedQueue<byte[]>();
//
//        private final ConcurrentLinkedQueue<byte[]> _writes = new ConcurrentLinkedQueue<byte[]>();
//
//        @Override
//        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
//            Object msg = e.getMessage();
//
//            if (msg instanceof ChannelBuffer) {
//                byte[] copy = new byte[buffer.limit()];
//                System.arraycopy(buffer.array(), 0, copy, 0, copy.length);
//                _reads.add(copy);
//            } 
//        }
//    }
//    
//    private class Connection extends NIOTransport {
//
//        @Override
//        protected void read(ByteBuffer buffer) {
//        }
//
//        @Override
//        protected boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
//            byte[] write = _writes.poll();
//            System.arraycopy(write, 0, buffer.array(), 0, write.length);
//            buffer.limit(write.length);
//            buffer.position(0);
//            return false;
//        }
//    }
//
//    @Override
//    protected byte[] exchange(byte[] buffer) {
//        _writes.add(buffer);
//        byte[] read = _reads.poll();
//        return read != null ? read : new byte[0];
//    }
//
//    public static void main(String[] args) throws Exception {
//        DistributedSerializationTest test = new DistributedSerializationTest();
//
//        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
//                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
//
//        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
//
//            @Override
//            public ChannelPipeline getPipeline() throws Exception {
//                ChannelPipeline pipeline = Channels.pipeline();
//                pipeline.addLast("objectfabric", new NettySession(resolver));
//                return pipeline;
//            }
//        });
//
//        bootstrap.bind(new InetSocketAddress(4444));
//        
//        test.test1();
//        test.test2();
//        test.test3();
//        test.test4();
//
//        Platform.reset();
//    }
//}