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

import org.junit.Test;

public class SerializationTestJava extends SerializationTest {

    static {
        JVMPlatform.loadClass();
    }

    @Test
    public void test1() {
        run(false, false);
    }

    @Test
    public void test2() {
        run(false, true);
    }

    @Test
    public void test3() {
        run(true, false);
    }

    @Test
    public void test4() {
        run(true, true);
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
        test.test3();
    }
}