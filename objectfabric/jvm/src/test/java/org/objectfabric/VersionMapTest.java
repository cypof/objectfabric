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
import org.objectfabric.TObject.Transaction;
import org.objectfabric.generated.SimpleClass;

public class VersionMapTest extends TestsHelper {

    private Workspace _w;

    @Test
    public void merge() {
        _w = Platform.newTestWorkspace();

        Transaction barrier1 = _w.startImpl(0);

        SimpleClass a = new SimpleClass(_w.resolve(""));
        a.int0(1);

        Transaction barrier2 = _w.startImpl(0);

        SimpleClass b = new SimpleClass(_w.resolve(""));
        b.int0(2);

        Assert.assertEquals(3, _w.snapshot().getVersionMaps().length);
        Assert.assertEquals(1, a.int0());
        Assert.assertEquals(2, b.int0());

        TransactionManager.abort(barrier2);

        Assert.assertEquals(2, _w.snapshot().getVersionMaps().length);
        Assert.assertEquals(1, a.int0());
        Assert.assertEquals(2, b.int0());

        TransactionManager.abort(barrier1);

        Assert.assertEquals(1, _w.snapshot().getVersionMaps().length);
        Assert.assertEquals(1, a.int0());
        Assert.assertEquals(2, b.int0());

        _w.close();
    }
}
