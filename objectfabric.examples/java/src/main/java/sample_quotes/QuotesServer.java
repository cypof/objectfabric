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

package sample_quotes;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.objectfabric.AbstractKeyListener;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Memory;
import org.objectfabric.NettySession;
import org.objectfabric.Resource;
import org.objectfabric.Server;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

import sample_quotes.generated.ObjectModel;
import sample_quotes.generated.Order;

public class QuotesServer {

    public static void main(String[] args) throws Exception {
        ObjectModel.register();

        Memory memory = new Memory(false);
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);

        /*
         * Send random quotes.
         */
        final Resource goog = workspace.open("/GOOG");
        final Resource msft = workspace.open("/MSFT");

        new Thread() {

            @Override
            public void run() {
                Random rand = new Random();

                for (;;) {
                    goog.set(new BigDecimal(rand.nextInt(100)));
                    msft.set(new BigDecimal(rand.nextInt(100)));

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }.start();

        /*
         * Listen for new orders.
         */
        final Resource orders = workspace.open("/orders");
        TSet<Order> set = new TSet<Order>(orders);
        orders.set(set);

        set.addListener(new AbstractKeyListener<Order>() {

            @Override
            public void onPut(Order order) {
                System.out.println("Order from " + order.user() + ": " + order.quantity() + " " + order.instrument());
            }
        });

        final Server server = new JVMServer();
        server.addURIHandler(memory);

        /*
         * Start a WebSocket server.
         */
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

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
        System.out.println("Started quotes server.");
        Thread.sleep(Long.MAX_VALUE);
    }
}
