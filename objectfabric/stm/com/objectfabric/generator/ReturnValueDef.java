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

package com.objectfabric.generator;

import com.objectfabric.ImmutableClass;
import com.objectfabric.misc.PlatformClass;

/**
 * Return value of a method.
 */
public class ReturnValueDef extends ValueDef {

    private static final String NAME = "return_objectfabric";

    public ReturnValueDef() {
        this(PlatformClass.getVoidClass());
    }

    public ReturnValueDef(java.lang.Class type) {
        this(new TypeDef(type));
    }

    public ReturnValueDef(GeneratedClassDef classDef) {
        this(new TypeDef(classDef));
    }

    public ReturnValueDef(ImmutableClass type) {
        this(new TypeDef(type));
    }

    public ReturnValueDef(TypeDef type) {
        super(type, NAME, null);
    }
}
