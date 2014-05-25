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

package org.objectfabric;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class LoadOverrideTest extends TestsHelper {

    @Test
    public void run1() {
        Memory memory = new Memory(false);

        Workspace workspace = Platform.newTestWorkspace();
        workspace.addURIHandler(memory);
        Resource resource = workspace.open("test:///object");
        resource.set("blah");
        Assert.assertEquals("blah", resource.get());
        workspace.close();

        workspace = Platform.newTestWorkspace();
        workspace.addURIHandler(memory);
        resource = workspace.open("test:///object");
        resource.set("blah2");
        Log.write("write");
        Assert.assertEquals("blah2", resource.get());
        workspace.close();
    }
}
