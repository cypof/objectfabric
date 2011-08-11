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

package of4gwt;

public class SerializationTestGWT extends SerializationTest {

    public void test1() {
        run(true, true, "");
    }

    public void test2() {
        run(true, false, "");
    }

    public void test3() {
        run(false, true, "");
    }

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
}