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

import org.junit.Assert;
import org.objectfabric.Address;
import org.objectfabric.Headers;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Permission;
import org.objectfabric.Resource;
import org.objectfabric.Workspace;

import part04.SchemesClient;

/**
 * OF supports a very simple permission model where resources can either not be accessed,
 * read (synchronizing changes downstream only), or read-write. Headers can be sent by
 * clients and servers to send credentials or tokens. Headers are sent using the
 * underlying transport headers if supported (e.g. as WebSocket request headers for client
 * to server), or as part as the OF protocol otherwise (e.g. for socket connections, or
 * WebSocket in the server to client direction).
 */
public class SecurityClient {

    public static void main(String[] args) throws Exception {
        Workspace workspace = new JVMWorkspace();

        /*
         * Using a secure connection as login and password are transfered in plain text.
         * Reusing SchemesClient's SSL handler to accept the self-signed certificate.
         */
        workspace.addURIHandler(new SchemesClient.TestURIHandler() {

            @Override
            protected Headers getHeaders(Address address) {
                Headers headers = new Headers();
                headers.add("user", "me");
                headers.add("pass", "pw");
                return headers;
            }
        });

        /*
         * This resource is not accessible.
         */
        Resource resource = workspace.open("wss://localhost:8883/internal");
        Assert.assertEquals(Permission.NONE, resource.permission());

        /*
         * This resource is read only.
         */
        resource = workspace.open("wss://localhost:8883/read-only");
        Assert.assertEquals(Permission.READ, resource.permission());

        /*
         * Resource content can be read.
         */
        Assert.assertEquals("data", resource.get());

        /*
         * Writes are allowed but will not be synchronized to server. Updates remain in
         * local memory and caches only.
         */
        resource.set("update");

        /*
         * This resource is read-write.
         */
        resource = workspace.open("wss://localhost:8883/read-write");
        Assert.assertEquals(Permission.WRITE, resource.permission());
        Assert.assertEquals("data", resource.get());
        resource.set("update");

        workspace.close();

        /*
         * Reload to see current server state.
         */
        workspace = new JVMWorkspace();
        workspace.addURIHandler(new SchemesClient.TestURIHandler());

        /*
         * Read-only is still at previous value.
         */
        Object value = workspace.open("wss://localhost:8883/read-only").get();
        Assert.assertEquals("data", value);

        /*
         * Read-write has been updated.
         */
        value = workspace.open("wss://localhost:8883/read-write").get();
        Assert.assertEquals("update", value);

        System.out.println("Done!");
        workspace.close();
    }
}
