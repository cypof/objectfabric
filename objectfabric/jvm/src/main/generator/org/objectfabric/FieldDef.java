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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * Field of a transactional class.
 */
@XmlType(name = "Field")
public class FieldDef extends ValueDef {

    public static final String TRUE = "true", FALSE = "false", READ = "read";

    /**
     * Read-only fields are set by object constructor and cannot be changed. Accessing a
     * read-only field has a lower overhead than transactional fields.
     */
    @XmlAttribute(required = false)
    public boolean Readonly;

    /**
     * Fields can be declared as public (TRUE), protected (FALSE), or public for the read
     * accessor and protected for the write one (READ).
     */
    @XmlAttribute(required = false)
    public String Public = TRUE;

    public FieldDef() {
    }

    public FieldDef(java.lang.Class type, String name) {
        this(new TypeDef(type), name, null);
    }

    public FieldDef(ClassDef classDef, String name) {
        this(new TypeDef(classDef), name, null);
    }

    public FieldDef(Immutable type, String name) {
        this(new TypeDef(type), name, null);
    }

    public FieldDef(TypeDef type, String name) {
        this(type, name, null);
    }

    public FieldDef(TypeDef type, String name, String description) {
        super(type, name, description);
    }

    @Override
    boolean isReadOnly() {
        return Readonly;
    }

    @Override
    String publicVisibility() {
        return Public;
    }
}
