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
//import java.net.InetSocketAddress;
//import java.util.ArrayList;
//import java.util.concurrent.Executors;
//
//import org.jboss.netty.bootstrap.ServerBootstrap;
//import org.jboss.netty.channel.ChannelPipeline;
//import org.jboss.netty.channel.ChannelPipelineFactory;
//import org.jboss.netty.channel.Channels;
//import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
//
//public class DistributedNetty extends Distributed {
//
//    void connect() {
//        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
//                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
//
//        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
//
//            @Override
//            public ChannelPipeline getPipeline() throws Exception {
//                ChannelPipeline pipeline = Channels.pipeline();
//                pipeline.addLast("objectfabric", new NettySession(workspace));
//                return pipeline;
//            }
//        });
//
//        _channel = bootstrap.bind(new InetSocketAddress(8080));
//
//        for (int i = 0; i < writers; i++) {
//            ArrayList<String> args = new ArrayList<String>();
//            args.add("" + i);
//            args.add("" + flags);
//
//            SeparateVM client = SeparateVM.start(DistributedClient.class.getName(), args, new Runnable() {
//
//                @Override
//                public void run() {
//                    _clients.remove(this);
//                }
//            });
//
//            _clients.put(client, client);
//        }
//    }
//}
