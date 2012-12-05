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

package sample_images;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.objectfabric.FileSystem;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettySession;
import org.objectfabric.Resource;
import org.objectfabric.Server;
import org.objectfabric.TArrayDouble;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

public class ImagesServer {

    public static void main(String[] args) throws Exception {
        FileSystem temp = new FileSystem("temp");
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(temp);
        Resource resource = workspace.open("/images");

        /*
         * Share a set arrays of doubles. Each array is of length two, representing the X
         * and Y coordinates of images. Also for convenience pass the generic type as
         * argument of the Set. It makes sure .NET instantiates a typed TSet.
         */
        if (resource.get() == null)
            resource.set(new TSet<TArrayDouble>(resource, TArrayDouble.TYPE));

        workspace.close();

        final Server server = new JVMServer();
        server.addURIHandler(temp);

        /*
         * Start a WebSocket server. (C.f. https://netty.io)
         */
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("handler", new NettySession(server));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(8888));
        System.out.println("Started Images server.");

        /*
         * When packaged as a demo, also launch Jetty to serve static files.
         */
        if (args != null && args.length == 1) {
            org.eclipse.jetty.server.Server jetty = new org.eclipse.jetty.server.Server();
            SelectChannelConnector connector = new SelectChannelConnector();
            connector.setPort(8080);
            jetty.addConnector(connector);
            ResourceHandler resource_handler = new ResourceHandler();
            resource_handler.setDirectoriesListed(true);
            resource_handler.setWelcomeFiles(new String[] { "images.html" });
            resource_handler.setResourceBase(args[0]);
            HandlerList handlers = new HandlerList();
            handlers.setHandlers(new Handler[] { resource_handler, new DefaultHandler() });
            jetty.setHandler(handlers);
            jetty.start();
            jetty.join();
        }
    }
}
