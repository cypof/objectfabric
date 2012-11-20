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

package part04;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Memory;
import org.objectfabric.NettySession;
import org.objectfabric.Server;
import org.objectfabric.Workspace;

public class SchemesServer {

    public static void main(String[] args) throws Exception {
        /*
         * Put a resource in a memory store.
         */
        Memory memory = new Memory(false);
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);
        workspace.resolve("/test").set("data");
        workspace.close();

        /*
         * Use memory store as default handler for all URIs.
         */
        final Server resolver = new JVMServer();
        resolver.addURIHandler(memory);

        /*
         * Start a socket server. (C.f. https://netty.io)
         */
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("objectfabric", new NettySession(resolver));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(1850));
        System.out.println("Started socket server on port 1850");

        /*
         * Start a secure socket server.
         */
        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        final SSLContext sslContext = createSSLContext();

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                pipeline.addLast("ssl", new SslHandler(engine));
                pipeline.addLast("objectfabric", new NettySession(resolver));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(1853));
        System.out.println("Started secure socket server on port 1853");

        /*
         * Start a WebSocket server.
         */
        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("objectfabric", new NettySession(resolver));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(8888));
        System.out.println("Started WebSocket server on port 8888");

        /*
         * Start a secure WebSocket server.
         */
        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                pipeline.addLast("ssl", new SslHandler(engine));
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("objectfabric", new NettySession(resolver));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(8883));
        System.out.println("Started secure WebSocket server on port 8883");
    }

    /**
     * Initiates a SSL context from self-signed certificate.
     */
    public static SSLContext createSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();
        ks.load(new FileInputStream("src/main/java/part04/keystore.jks"), passphrase);
        ts.load(new FileInputStream("src/main/java/part04/keystore.jks"), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ssl;
    }
}
