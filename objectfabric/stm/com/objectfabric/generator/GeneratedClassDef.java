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
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformSet;

@XmlType(name = "Class")
public class GeneratedClassDef extends ValueSetDef {

    @XmlAttribute
    public String Parent;

    @XmlAttribute(required = false)
    public boolean Abstract;

    @XmlElement(name = "Field")
    public java.util.List<FieldDef> Fields = new List.FullImpl<FieldDef>();

    @XmlElement(name = "Method")
    public java.util.List<MethodDef> Methods = new List.FullImpl<MethodDef>();

    private PackageDef _package;

    private TypeDef _parent;

    public GeneratedClassDef() {
    }

    public GeneratedClassDef(String name) {
        this(name, null);
    }

    public GeneratedClassDef(String name, String description) {
        super(name, description);
    }

    PackageDef getPackage() {
        return _package;
    }

    void setPackage(PackageDef value) {
        _package = value;
    }

    @Override
    String getActualName(ObjectModelDef model) {
        return Name + (Abstract ? "Base" : "");
    }

    String getFullName() {
        String s = "";

        if (_package != null)
            s += _package.getFullName() + ".";

        return s + Name;
    }

    @XmlTransient
    @Override
    public TypeDef getParent() {
        return _parent;
    }

    public void setParent(GeneratedClassDef value) {
        setParent(new TypeDef(value));
    }

    public void setParent(java.lang.Class value) {
        setParent(new TypeDef(value));
    }

    public void setParent(TypeDef value) {
        _parent = value;
    }

    @Override
    GeneratedClassDef getParentGeneratedClass(ObjectModelDef model) {
        return _parent != null ? _parent.getFirstGeneratedClassAmongstThisAndParents(model) : null;
    }

    PlatformSet<GeneratedClassDef> getAllChildren(ObjectModelDef model) {
        PlatformSet<GeneratedClassDef> set = new PlatformSet<GeneratedClassDef>();

        for (int i = 0; i < model.getAllClasses().size(); i++) {
            GeneratedClassDef definition = model.getAllClasses().get(i);
            GeneratedClassDef parent = definition.getParentGeneratedClass(model);

            while (parent != null) {
                if (parent == this)
                    set.add(definition);

                parent = parent.getParentGeneratedClass(model);
            }
        }

        return set;
    }

    void computeMethodIndexesInClass(ObjectModelDef model) {
        List<MethodDef> all = new List<MethodDef>();
        addMethodsForThisAndParents(all, model);

        for (int i = 0; i < Methods.size(); i++)
            Methods.get(i).setIndexInClass(all.indexOf(Methods.get(i)));
    }

    void addMethodsForThisAndParents(List<MethodDef> list, ObjectModelDef model) {
        GeneratedClassDef parent = getParentGeneratedClass(model);

        if (parent != null)
            parent.addMethodsForThisAndParents(list, model);

        for (int i = 0; i < Methods.size(); i++)
            list.add(Methods.get(i));
    }

    boolean isTGeneratedFields(ObjectModelDef model) {
        if (getParent() != null)
            return getParent().isTGeneratedFields(model);

        return true;
    }

    @Override
    boolean lessOr32Fields(ObjectModelDef model) {
        // Find max field count for whole hierarchy

        for (GeneratedClassDef child : getAllChildren(model))
            if (child.getAllValues(model).size() > 32)
                return false;

        return super.lessOr32Fields(model);
    }

    @Override
    List<ValueDef> createValues() {
        List<ValueDef> values = new List<ValueDef>();

        for (int i = 0; i < Fields.size(); i++)
            values.add(Fields.get(i));

        return values;
    }

    void visit(Visitor visitor) {
        visitor.visit(this);
    }
}
