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

/**
 * Base for class fields and method arguments and return values.
 */
public class ValueDef {

    static {
        JVMPlatform.loadClass();
    }

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

    public ValueDef(TypeDef type, String name, String comment) {
        if (type == null)
            throw new IllegalArgumentException();

        _type = type;
        Name = name;
        Comment = comment;
    }

    TypeDef type() {
        return _type;
    }

    void type(TypeDef value) {
        _type = value;
    }

    String nameAsConstant() {
        if (_constant == null)
            _constant = Utils.getNameAsConstant(Name);

        return _constant;
    }

    boolean isReadOnly() {
        return false;
    }

    String publicVisibility() {
        return FieldDef.TRUE;
    }

    void visit(ModelVisitor visitor) {
        visitor.visit(this);
    }
}
