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
import org.objectfabric.TIndexed.Version32;
import org.objectfabric.TIndexed.VersionN;
import org.objectfabric.generated.Limit32;
import org.objectfabric.generated.Limit32_max;
import org.objectfabric.generated.LimitN;
import org.objectfabric.generated.LimitN_min;

public class GeneratorTest {

    @SuppressWarnings("cast")
    @Test
    public void run() {
        JVMPlatform.loadClass();
        Workspace workspace = Platform.newTestWorkspace();
        Resource _ = workspace.resolve("");

        Assert.assertTrue(((TObject) new Limit32(_)).createVersion_() instanceof Version32);
        Assert.assertTrue(((TObject) new Limit32_max(_)).createVersion_() instanceof Version32);
        Assert.assertTrue(((TObject) new LimitN_min(_)).createVersion_() instanceof VersionN);
        Assert.assertTrue(((TObject) new LimitN(_)).createVersion_() instanceof VersionN);

        workspace.close();
    }
}
