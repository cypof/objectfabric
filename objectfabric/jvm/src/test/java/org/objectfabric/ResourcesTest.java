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
import org.junit.Test;

public class ResourcesTest extends TestsHelper {

    @Test
    public void test() {
        JVMPlatform.loadClass();
        Workspace workspace = Platform.newTestWorkspace();

        Resources resources = new Resources();
        Resource a = Platform.newTestResource(workspace);
        Resource b = Platform.newTestResource(workspace);
        resources.add(a);
        resources.add(b);
        Assert.assertEquals(2, resources.size());
        resources.add(a);
        Assert.assertEquals(2, resources.size());
        resources.pollPartOfClear();
        Assert.assertEquals(1, resources.size());
        resources.add(a);
        Assert.assertEquals(1, resources.size());
        resources.add(b);
        Assert.assertEquals(2, resources.size());
        resources.pollPartOfClear();
        Assert.assertEquals(1, resources.size());
        resources.pollPartOfClear();
        Assert.assertEquals(0, resources.size());
        workspace.close();
    }
}
