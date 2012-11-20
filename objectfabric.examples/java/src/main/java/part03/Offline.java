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

package part03;

import org.junit.Assert;
import org.objectfabric.ClientURIHandler;
import org.objectfabric.FileSystemCache;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.Remote;
import org.objectfabric.Remote.Status;
import org.objectfabric.Resource;
import org.objectfabric.TMap;
import org.objectfabric.Workspace;

/**
 * This examples shows how resources can be cached locally and updated off-line. Updates
 * are synchronized to the server when connectivity is restored.
 */
@SuppressWarnings("unchecked")
public class Offline {

    public static void main(String[] args) throws Exception {
        /*
         * Create a file based cache. Clear it to get a predictable test.
         */
        FileSystemCache cache = new FileSystemCache("temp/offline");
        cache.clear();

        /*
         * Get a resource from server and store it in a local cache.
         */
        {
            Workspace workspace = new JVMWorkspace();
            workspace.addURIHandler(new NettyURIHandler());

            /*
             * Register the cache and get the resource.
             */
            workspace.addCache(cache);
            workspace.resolve("ws://localhost:8888/map").get();

            // Wait for data to get cached (no API for this yet)
            Thread.sleep(500);

            workspace.close();
        }

        /*
         * To test that objects are now in the cache, reload them from cache.
         */
        {
            Workspace workspace = new JVMWorkspace();
            workspace.addURIHandler(new NettyURIHandler());
            workspace.addCache(cache);

            /*
             * First disable network. (Testing purposes only)
             */
            ClientURIHandler.disableNetwork();

            /*
             * Then resolve resource and check its origin is actually disconnected.
             */
            Resource resource = workspace.resolve("ws://localhost:8888/map");
            Remote remote = (Remote) resource.origin();
            Assert.assertEquals(Status.DISCONNECTED, remote.status());

            /*
             * Load the map from cache. Map can still be updated off-line.
             */
            TMap<String, Integer> map = (TMap) resource.get();
            Assert.assertEquals(42, (int) map.get("example key"));
            map.put("offline update", 43);
            workspace.close();
        }

        /*
         * If network becomes later accessible and resource is loaded again, its update
         * will be synchronized to the server.
         */
        {
            Workspace workspace = new JVMWorkspace();
            workspace.addURIHandler(new NettyURIHandler());
            workspace.addCache(cache);

            ClientURIHandler.enableNetwork();

            workspace.resolve("ws://localhost:8888/map").get();

            // Wait for data to get sent (no API for this yet)
            Thread.sleep(500);

            workspace.close();
        }

        /*
         * To make sure resource has been synchronized with server, clear the cache and
         * load resource again from server.
         */
        {
            cache.clear();

            Workspace workspace = new JVMWorkspace();
            workspace.addURIHandler(new NettyURIHandler());
            TMap<String, Integer> map = (TMap) workspace.resolve("ws://localhost:8888/map").get();
            Assert.assertEquals(42, (int) map.get("example key"));
            Assert.assertEquals(43, (int) map.get("offline update"));
            workspace.close();
        }
    }
}
