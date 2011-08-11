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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.objectfabric.generator.Generator.Flag;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.Utils;

@XmlType(name = "Method")
public class MethodDef extends ValueSetDef {

    @XmlElement(name = "Argument")
    public java.util.List<ArgumentDef> Arguments = new List.FullImpl<ArgumentDef>();

    public ReturnValueDef ReturnValue = new ReturnValueDef();

    @XmlAttribute(required = false)
    public boolean NoCustomExecutor;

    private int _index = -1;

    public MethodDef() {
        this(null, null);
    }

    public MethodDef(String name, String description) {
        super(name, description);

        ReturnValue.Type = "void";
    }

    int getIndexInClass() {
        return _index;
    }

    void setIndexInClass(int value) {
        Debug.assertAlways(_index == -1 || value == _index);
        _index = value;
    }

    @Override
    String getActualName(ObjectModelDef model) {
        return "Method" + model.getAllMethods().indexOf(this);
    }

    String getNameWithRightCaps(Generator gen) {
        if (gen.getFlags().contains(Flag.NO_METHOD_NAME_CAPS_CHANGE))
            return Name;

        return gen.isJava() ? Utils.getWithFirstLetterDown(Name) : Utils.getWithFirstLetterUp(Name);
    }

    @Override
    TypeDef getParent() {
        return null;
    }

    @Override
    GeneratedClassDef getParentGeneratedClass(ObjectModelDef model) {
        return null;
    }

    String getFullType(Generator generator) {
        String model = generator.getObjectModel().getFullNameNonAbstract();
        int globalIndex = generator.getObjectModel().getAllMethods().indexOf(this);
        return model + ".Method" + globalIndex;
    }

    @Override
    List<ValueDef> createValues() {
        List<ValueDef> values = new List<ValueDef>();

        for (int i = 0; i < Arguments.size(); i++)
            values.add(Arguments.get(i));

        if (ReturnValue.getType().getOtherClass() != PlatformClass.getVoidClass())
            values.add(ReturnValue);

        values.add(new ValueDef(new TypeDef(PlatformClass.getStringClass()), "error_objectfabric", ""));
        return values;
    }
}
