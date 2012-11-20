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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "Class")
public class ClassDef extends ValueSetDef {

    @XmlAttribute
    public String Parent;

    @XmlElement(name = "Field")
    public java.util.List<FieldDef> Fields = new java.util.ArrayList<FieldDef>();

    @XmlElement(name = "Method")
    public java.util.List<MethodDef> Methods = new java.util.ArrayList<MethodDef>();

    private PackageDef _package;

    private TypeDef _parent;

    private boolean _abstract;

    public ClassDef() {
    }

    public ClassDef(String name) {
        this(name, null);
    }

    public ClassDef(String name, String description) {
        super(name, description);
    }

    final boolean getAbstract() {
        return _abstract;
    }

    final void setAbstract() {
        _abstract = true;
    }

    PackageDef getPackage() {
        return _package;
    }

    void setPackage(PackageDef value) {
        _package = value;
    }

    @Override
    String actualName(ObjectModelDef model) {
        return Name + (_abstract ? "Base" : "");
    }

    String fullName() {
        String s = "";

        if (_package != null)
            s += _package.fullName() + ".";

        return s + Name;
    }

    @Override
    public TypeDef parent() {
        return _parent;
    }

    public void parent(ClassDef value) {
        parent(new TypeDef(value));
    }

    public void parent(java.lang.Class value) {
        parent(new TypeDef(value));
    }

    public void parent(TypeDef value) {
        _parent = value;
    }

    @Override
    ClassDef parentGeneratedClass(ObjectModelDef model) {
        return _parent != null ? _parent.firstGeneratedClassAmongstThisAndParents(model) : null;
    }

    PlatformSet<ClassDef> getAllChildren(ObjectModelDef model) {
        PlatformSet<ClassDef> set = new PlatformSet<ClassDef>();

        for (int i = 0; i < model.allClasses().size(); i++) {
            ClassDef definition = model.allClasses().get(i);
            ClassDef parent = definition.parentGeneratedClass(model);

            while (parent != null) {
                if (parent == this)
                    set.add(definition);

                parent = parent.parentGeneratedClass(model);
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
        ClassDef parent = parentGeneratedClass(model);

        if (parent != null)
            parent.addMethodsForThisAndParents(list, model);

        for (int i = 0; i < Methods.size(); i++)
            list.add(Methods.get(i));
    }

    boolean isTGeneratedFields(ObjectModelDef model) {
        if (parent() != null)
            return parent().isTGeneratedFields(model);

        return true;
    }

    @Override
    boolean lessOr32Fields(ObjectModelDef model) {
        // Find max field count for whole hierarchy

        for (ClassDef child : getAllChildren(model))
            if (child.allValues(model).size() > 32)
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

    void visit(ModelVisitor visitor) {
        visitor.visit(this);
    }
}
