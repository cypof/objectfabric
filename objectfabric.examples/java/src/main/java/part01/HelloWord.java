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

package part01;

import org.junit.Assert;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.Workspace;

/**
 * Fetches a resource from a server. Other versions of this sample allow connections to
 * the same server from JavaScript, GWT and .NET.
 */
public class HelloWord {

    public static void main(String[] args) {
        /*
         * Create the workspace in which resources will be loaded. This is like opening a
         * browser.
         */
        Workspace workspace = new JVMWorkspace();

        /*
         * Add a network transport to access remote resources.
         */
        workspace.addURIHandler(new NettyURIHandler());

        /*
         * Get resource from server. (Server 'launchfirst.ExamplesServer' must be running)
         */
        Object value = workspace.resolve("ws://localhost:8888/helloworld").get();
        Assert.assertEquals("Hello World!", value);
        System.out.println(value);

        /*
         * Close workspace to release its resources and allow connection to expire.
         */
        workspace.close();
    }
}
