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

package part06;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import junit.framework.Assert;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.objectfabric.Address;
import org.objectfabric.FileSystem;
import org.objectfabric.JVMServer;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Location;
import org.objectfabric.Memory;
import org.objectfabric.NettySession;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.Origin;
import org.objectfabric.Remote;
import org.objectfabric.Resource;
import org.objectfabric.Server;
import org.objectfabric.URI;
import org.objectfabric.URIHandler;
import org.objectfabric.Workspace;

/**
 * A {@link Resource} is stored and loaded from a {@link Location}. When a
 * {@link Workspace} or {@link Server} resolves a URI, it goes over its list of registered
 * {@link URIHandler} until one is able to resolve its location. Locations can also be
 * used as caches. Built-in locations include an in-memory store, {@link Memory}, and a
 * file-based one, {@link FileSystem}. Some OF extensions are implemented as additional
 * locations.
 */
public class Locations {

    public static void main(String[] args) throws Exception {
        /*
         * Create an in-memory store which is not used as a cache.
         */
        Memory memory = new Memory(false);

        /*
         * Add it to a workspace as default location for all URIs.
         */
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);

        /*
         * Resolving a URI returns an empty resource with memory as origin location.
         */
        Resource resource = workspace.resolve("/test");
        Assert.assertEquals(memory, resource.origin());

        /*
         * Any valid URI can be used and will resolve to memory.
         */
        resource = workspace.resolve("any:///blah");
        Assert.assertEquals(memory, resource.origin());

        /*
         * Data can be stored and reloaded by another Workspace or Server.
         */
        resource.set("data");
        workspace.close();
        workspace = new JVMWorkspace();
        workspace.addURIHandler(memory);
        resource = workspace.resolve("memory:///blah");
        Assert.assertEquals("data", resource.get());
        workspace.close();

        /*
         * A handler can be used to customize URI resolution.
         */
        final Origin locationA = new Memory(false);
        final Origin locationB = new FileSystem("temp");

        URIHandler handler = new URIHandler() {

            @Override
            public URI handle(Address address, String path) {
                if ("memory".equals(address.Scheme))
                    return locationA.getURI(path);

                if (path.startsWith("/folder"))
                    return locationB.getURI(path);

                return null;
            }
        };

        workspace = new JVMWorkspace();
        workspace.addURIHandler(handler);

        Assert.assertEquals(locationA, workspace.resolve("memory:///data").origin());
        Assert.assertEquals(locationB, workspace.resolve("/folder").origin());

        try {
            workspace.resolve("/not");
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains("URI cannot be resolved"));
        }

        workspace.close();

        /*
         * Servers use the same URI handlers and locations mechanism as workspaces.
         */
        final Server server = new JVMServer();
        server.addURIHandler(handler);

        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory( //
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("objectfabric", new NettySession(server));
                return pipeline;
            }
        });

        bootstrap.bind(new InetSocketAddress(1850));

        /*
         * Connect a client.
         */
        workspace = new JVMWorkspace();
        workspace.addURIHandler(new NettyURIHandler());

        /*
         * Client side, resolving a URI returns a resource whose location is the network.
         */
        resource = workspace.resolve("tcp://localhost:1850/folder");
        Assert.assertTrue(resource.origin() instanceof Remote);
        Assert.assertNull(resource.get());

        /*
         * This one correctly resolves client side to the network, but cannot be resolved
         * server side, so get() will fail.
         */
        resource = workspace.resolve("tcp://localhost:1850/not");

        try {
            resource.get();
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains("URI cannot be resolved"));
        }

        System.out.println("Done!");
        workspace.close();
    }
}
