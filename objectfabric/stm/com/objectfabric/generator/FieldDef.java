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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import com.objectfabric.ImmutableClass;

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

    /**
     * Fields can be declared as transient. This is a hint for stores implementations that
     * this field should not be persisted.
     */
    @XmlAttribute(required = false)
    public boolean Transient;

    public FieldDef() {
    }

    public FieldDef(java.lang.Class type, String name) {
        this(new TypeDef(type), name, null);
    }

    public FieldDef(GeneratedClassDef classDef, String name) {
        this(new TypeDef(classDef), name, null);
    }

    public FieldDef(ImmutableClass type, String name) {
        this(new TypeDef(type), name, null);
    }

    public FieldDef(TypeDef type, String name) {
        this(type, name, null);
    }

    public FieldDef(TypeDef type, String name, String description) {
        super(type, name, description);
    }

    @Override
    @XmlTransient
    boolean isReadOnly() {
        return Readonly;
    }

    @Override
    @XmlTransient
    String getPublic() {
        return Public;
    }

    @Override
    @XmlTransient
    boolean isTransient() {
        return Transient;
    }
}
