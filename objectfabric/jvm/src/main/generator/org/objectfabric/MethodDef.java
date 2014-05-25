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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "Method")
public class MethodDef extends ValueSetDef {

    @XmlElement(name = "Argument")
    public java.util.List<ArgumentDef> Arguments = new java.util.ArrayList<ArgumentDef>();

    public ReturnValueDef ReturnValue = new ReturnValueDef();

    private int _index = -1;

    public MethodDef() {
        this(null, null);
    }

    public MethodDef(String name, String description) {
        super(name, description);

        ReturnValue.Type = "void";
    }

    int indexInClass() {
        return _index;
    }

    void setIndexInClass(int value) {
        Debug.assertAlways(_index == -1 || value == _index);
        _index = value;
    }

    @Override
    String actualName(ObjectModelDef model) {
        return "Method" + model.allMethods().indexOf(this);
    }

    String nameWithRightCaps(GeneratorBase gen) {
        return gen.isJava() ? Utils.getWithFirstLetterDown(Name) : Utils.getWithFirstLetterUp(Name);
    }

    @Override
    TypeDef parent() {
        return null;
    }

    @Override
    ClassDef parentGeneratedClass(ObjectModelDef model) {
        return null;
    }

    String fullType(GeneratorBase generator) {
        String model = generator.objectModel().fullNameNonAbstract();
        int globalIndex = generator.objectModel().allMethods().indexOf(this);
        return model + ".Method" + globalIndex;
    }

    @Override
    List<ValueDef> createValues() {
        List<ValueDef> values = new List<ValueDef>();

        for (int i = 0; i < Arguments.size(); i++)
            values.add(Arguments.get(i));

        if (ReturnValue.type().otherClass() != Platform.get().voidClass())
            values.add(ReturnValue);

        values.add(new ValueDef(new TypeDef(Platform.get().stringClass()), "_error", ""));
        return values;
    }
}
