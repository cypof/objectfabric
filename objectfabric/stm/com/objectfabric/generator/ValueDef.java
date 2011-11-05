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

import com.objectfabric.misc.Utils;

/**
 * Base for class fields and method arguments and return values.
 */
public class ValueDef {

    @XmlAttribute
    public String Type;

    @XmlAttribute
    public String Name;

    @XmlAttribute
    public String Comment;

    private TypeDef _type;

    private String _constant;

    public ValueDef() {
    }

    TypeDef getType() {
        return _type;
    }

    void setType(TypeDef value) {
        _type = value;
    }

    public ValueDef(TypeDef type, String name, String comment) {
        if (type == null)
            throw new IllegalArgumentException();

        _type = type;
        Name = name;
        Comment = comment;
    }

    String getNameAsConstant() {
        if (_constant == null)
            _constant = Utils.getNameAsConstant(Name);

        return _constant;
    }

    boolean isReadOnly() {
        return false;
    }

    String getPublic() {
        return FieldDef.TRUE;
    }

    boolean isTransient() {
        return false;
    }

    void visit(Visitor visitor) {
        visitor.visit(this);
    }
}
