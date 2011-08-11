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

import com.objectfabric.TGeneratedFields32;
import com.objectfabric.TGeneratedFieldsN;
import com.objectfabric.TIndexed32Version;
import com.objectfabric.TIndexedNVersion;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.Limit32_max;
import com.objectfabric.generated.LimitN;
import com.objectfabric.generated.LimitN_min;

public class GeneratorTest {

    @SuppressWarnings("cast")
    @Test
    public void run() {
        Assert.assertTrue(new Limit32().getSharedVersion_objectfabric().createVersion() instanceof TIndexed32Version);
        Assert.assertTrue(new Limit32_max().getSharedVersion_objectfabric().createVersion() instanceof TIndexed32Version);
        Assert.assertTrue(new LimitN_min().getSharedVersion_objectfabric().createVersion() instanceof TIndexedNVersion);
        Assert.assertTrue(new LimitN().getSharedVersion_objectfabric().createVersion() instanceof TIndexedNVersion);

        Assert.assertTrue(new Limit32() instanceof TGeneratedFields32);
        Assert.assertTrue(new Limit32_max() instanceof TGeneratedFields32);
        Assert.assertTrue(new LimitN_min() instanceof TGeneratedFieldsN);
        Assert.assertTrue(new LimitN() instanceof TGeneratedFieldsN);
    }
}
