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

package part08;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.objectfabric.Address;
import org.objectfabric.Headers;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Memory;
import org.objectfabric.NettySession;
import org.objectfabric.Permission;
import org.objectfabric.PermissionCallback;
import org.objectfabric.Server;
import org.objectfabric.Session;
import org.objectfabric.URI;
import org.objectfabric.Workspace;

import part04.SchemesServer;

public class SecurityServer {

    public static void main(String[] args) throws Exception {
        /*
         * Put some resources in a memory store.
         */
        Memory memory = new Memory(false);
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);
        workspace.resolve("/internal").set("data");
        workspace.resolve("/read-only").set("data");
        workspace.resolve("/read-write").set("data");
        workspace.close();

        final Server server = new JVMServer() {

            /*
             * This method allows creating custom sessions, either to hold state for each
             * client or to set permissions on requests. Security can be implemented on
             * top of this mechanism, e.g. using the Apache Shiro OF extension.
             */
            @Override
            protected Session onConnection(Object source, Address target, final Headers headers, Object channel) {
                final InetSocketAddress from = (InetSocketAddress) source;

                return new Session() {

                    /*
                     * Example of taking a permission decision based on client address,
                     * headers and URI path.
                     */
                    @Override
                    public void onRequest(URI uri, PermissionCallback callback) {
                        if (!from.getAddress().getHostAddress().equals("127.0.0.1")) {
                            callback.set(Permission.REJECT);
                            return;
                        }

                        if (headers != null) {
                            if (!"me".equals(headers.get("user")) || !"pw".equals(headers.get("pass"))) {
                                callback.set(Permission.REJECT);
                                return;
                            }

                            if (uri.path().equals("/read-only")) {
                                callback.set(Permission.READ);
                                return;
                            }

                            if (uri.path().equals("/read-write")) {
                                callback.set(Permission.WRITE);
                                return;
                            }
                        }

                        callback.set(Permission.REJECT);
                    }

                    @Override
                    public Headers sendResponseHeaders() {
                        return null;
                    }
                };
            }
        };

        /*
         * Use memory store as default handler for all URIs.
         */
        server.addURIHandler(memory);

        /*
         * Start a secure WebSocket server.
         */
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        final SSLContext sslContext = SchemesServer.createSSLContext();

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(false);
                pipeline.addLast("ssl", new SslHandler(engine));
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("objectfabric", new NettySession(server));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(8883));
        System.out.println("Started secure WebSocket server on port 8883");
    }
}
