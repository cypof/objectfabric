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

import org.junit.Test;

import com.objectfabric.misc.PlatformAdapter;

public class SerializationTestJava extends SerializationTest {

    @Test
    public void test1() {
        run(true, true, "");
    }

    @Test
    public void test2() {
        run(true, false, "");
    }

    @Test
    public void test3() {
        run(false, true, "");
    }

    @Test
    public void test4() {
        run(false, false, "");
    }

    @Override
    protected TestWriter createTestWriter(boolean unknown) {
        if (unknown)
            return new SerializationTestWriterUnknown();

        return new SerializationTestWriter();
    }

    @Override
    protected TestReader createTestReader(boolean unknown) {
        if (unknown)
            return new SerializationTestReaderUnknown();

        return new SerializationTestReader();
    }

    public static void main(String[] args) throws Exception {
        SerializationTestJava test = new SerializationTestJava();
        test.test4();
        PlatformAdapter.reset();
    }
}