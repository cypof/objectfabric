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

package part07;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.junit.Assert;
import org.objectfabric.Address;
import org.objectfabric.CustomLocation;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Memory;
import org.objectfabric.Resource;
import org.objectfabric.URI;
import org.objectfabric.URIHandler;

/**
 * A {@link CustomLocation} lets an application specify its own resource storage and data
 * format, for interoperability and to avoid depending on OF's default representation.
 */
public class CustomLocations {

    public static void main(String[] args) throws Exception {
        /*
         * Create a custom location that stores data as text in a map. Use an in-memory
         * store as backing for synchronization and caching (C.f. comments on
         * CustomLocation for more info).
         */
        final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<String, String>();

        final CustomLocation location = new CustomLocation(new Memory(false)) {

            @Override
            protected void onGet(Resource resource) {
                String key = resource.uri().path();
                String value = map.get(key);
                resource.set(value);
            }

            @Override
            protected void onChange(Resource resource, Runnable ack) {
                String key = resource.uri().path();
                String value = (String) resource.get();
                map.put(key, value);
                ack.run();
            }
        };

        /*
         * Use it to resolve all URIs.
         */
        URIHandler handler = new URIHandler() {

            @Override
            public URI handle(Address address, String path) {
                return location.getURI(path);
            }
        };

        JVMWorkspace workspace = new JVMWorkspace();
        workspace.addURIHandler(handler);

        /*
         * Add pre-existing data to the custom location and load it.
         */
        map.put("/key1", "value1");
        Assert.assertEquals("value1", workspace.open("/key1").get());

        /*
         * Add some new data.
         */
// TODO
//        workspace.open("/key2").set("value2");
//        workspace.flush();
//        Assert.assertEquals("value2", map.get("/key2"));

        System.out.println("Done!");
        workspace.close();
    }
}
