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

import javax.xml.bind.annotation.XmlType;

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

    public ArgumentDef(ClassDef classDef, String name) {
        super(new TypeDef(classDef), name, null);
    }

    public ArgumentDef(Immutable type, String name) {
        super(new TypeDef(type), name, null);
    }

    public ArgumentDef(TypeDef type, String name, String description) {
        super(type, name, description);
    }
}
