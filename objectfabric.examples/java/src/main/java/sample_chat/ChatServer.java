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

package sample_chat;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Memory;
import org.objectfabric.NettySession;
import org.objectfabric.Resource;
import org.objectfabric.Server;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

public class ChatServer {

    public static void main(String[] args) throws Exception {
        /*
         * Chats will be saved in memory.
         */
        Memory memory = new Memory(false);
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);

        /*
         * Create a chat room.
         */
        Resource resource = workspace.resolve("/room1");
        resource.set(new TSet(resource));
        workspace.close();

        /*
         * Use memory store as default handler for all URIs.
         */
        final Server server = new JVMServer();
        server.addURIHandler(memory);

        /*
         * Start a WebSocket server. (C.f. https://netty.io)
         */
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("objectfabric", new NettySession(server));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(8888));
        System.out.println("Started chat server");
    }
}
