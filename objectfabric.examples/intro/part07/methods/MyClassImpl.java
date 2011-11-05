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

package part07.methods;

import part07.methods.generated.MyClass;

public class MyClassImpl extends MyClass {

    @Override
    protected int startImplementation(String text) {
        System.out.println("Start (" + text + ")");
        return 42;
    }

    @Override
    protected void stopImplementation() {
        System.out.println("Stop");
    }
}
