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

import javax.xml.bind.annotation.XmlType;

import com.objectfabric.ImmutableClass;

/**
 * Argument of a method.
 */
@XmlType(name = "Argument")
public class ArgumentDef extends ValueDef {

    public ArgumentDef() {
    }

    public ArgumentDef(java.lang.Class type, String name) {
        super(new TypeDef(type), name, null);
    }

    public ArgumentDef(GeneratedClassDef classDef, String name) {
        super(new TypeDef(classDef), name, null);
    }

    public ArgumentDef(ImmutableClass type, String name) {
        super(new TypeDef(type), name, null);
    }

    public ArgumentDef(TypeDef type, String name, String description) {
        super(type, name, description);
    }
}
