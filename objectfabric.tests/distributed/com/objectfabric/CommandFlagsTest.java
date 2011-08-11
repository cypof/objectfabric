/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.ImmutableClass;
import com.objectfabric.TObjectWriter;
import com.objectfabric.misc.Debug;

public class CommandFlagsTest {

    @Test
    public void run1() {
        Debug.assertion(TObjectWriter.FLAG_TOBJECT == -128);

        for (ImmutableClass c : ImmutableClass.ALL) {
            Assert.assertTrue((c.ordinal() & TObjectWriter.FLAG_TOBJECT) == 0);
            Assert.assertTrue((c.ordinal() & TObjectWriter.FLAG_IMMUTABLE) == 0);
        }
    }
}
