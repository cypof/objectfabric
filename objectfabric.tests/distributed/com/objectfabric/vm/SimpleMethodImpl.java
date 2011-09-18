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

package com.objectfabric.vm;

import com.objectfabric.TestsHelper;
import com.objectfabric.misc.Debug;
import com.objectfabric.vm.generated.SimpleMethod;

public class SimpleMethodImpl extends SimpleMethod {

    public static final String ERROR = "error";

    public static final String ERROR_MESSAGE = "blah";

    @Override
    protected String methodImplementation(String sql, SimpleMethod eg) {
        if (ERROR.equals(sql)) {
            Debug.expectException();
            TestsHelper.throwRuntimeException(ERROR_MESSAGE);
        }

        if (sql != null)
            return sql;

        int i = getInt();
        setInt(i + 23);
        setInt2(i + 55);
        System.out.println("[methodImplementation] " + i);
        eg.setInt(i);
        return "" + i;
    }
}
