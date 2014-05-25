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

import java.util.Arrays;
import java.util.Comparator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.objectfabric.ModelVisitor.AfterUnmarshall;
import org.objectfabric.ModelVisitor.BeforeMarshall;

@XmlRootElement(name = "ObjectModel")
public class ObjectModelDef {

    static {
        JVMPlatform.loadClass();
    }

    @XmlAttribute
    public String Name = "ObjectModel";

    @XmlElement(name = "Package")
    public java.util.List<PackageDef> Packages = new java.util.ArrayList<PackageDef>();

    @XmlAttribute(required = false)
    public boolean Public = true;

    private final List<PackageDef> _allPackages = new List<PackageDef>();

    private final List<ClassDef> _allClasses = new List<ClassDef>();

    private final List<MethodDef> _allMethods = new List<MethodDef>();

    private List<String> _cachedClassNames;

    private List<String> _cachedUppercaseClassNames;

    private List<String> _cachedMethodIds;

    private boolean _skip;

    public ObjectModelDef() {
    }

    public ObjectModelDef(String name) {
        this();

        Name = name;
    }

    final boolean skip() {
        return _skip;
    }

    final void setSkip() {
        _skip = true;
    }

    final String fullName() {
        return rootPackage().fullName() + "." + Name;
    }

    final String fullNameNonAbstract() {
        return rootPackage().fullName() + "." + Name;
    }

    final PackageDef rootPackage() {
        if (Packages.size() == 0)
            throw new IllegalArgumentException("Object model needs to define at least one package.");

        return Packages.get(0);
    }

    final List<PackageDef> allPackages() {
        return _allPackages;
    }

    final List<ClassDef> allClasses() {
        return _allClasses;
    }

    final List<MethodDef> allMethods() {
        return _allMethods;
    }

    final List<String> allFullClassNames() {
        if (_cachedClassNames == null) {
            _cachedClassNames = new List<String>();
            List<ClassDef> temp = allClasses();

            for (int i = 0; i < temp.size(); i++)
                _cachedClassNames.add(temp.get(i).fullName());
        }

        return _cachedClassNames;
    }

    final List<String> allClassIds() {
        if (_cachedUppercaseClassNames == null) {
            _cachedUppercaseClassNames = new List<String>();
            List<String> temp = allFullClassNames();

            for (int i = 0; i < temp.size(); i++)
                _cachedUppercaseClassNames.add(formatAsConstant(temp.get(i)) + "_CLASS_ID");
        }

        return _cachedUppercaseClassNames;
    }

    final List<String> allMethodIds() {
        if (_cachedMethodIds == null) {
            _cachedMethodIds = new List<String>();

            for (int i = 0; i < allMethods().size(); i++)
                _cachedMethodIds.add(formatAsConstant(fullName()) + "_METHOD_" + i + "_ID");
        }

        return _cachedMethodIds;
    }

    static String formatAsConstant(String name) {
        return name.toUpperCase().replace('.', '_');
    }

    final void prepare() {
        _allPackages.clear();
        _allClasses.clear();
        _allMethods.clear();
        _cachedClassNames = null;
        _cachedUppercaseClassNames = null;
        _cachedMethodIds = null;

        for (int i = 0; i < Packages.size(); i++)
            gather(Packages.get(i));

        // Sort so that ids sequence is always the same

        ClassDef[] array = new ClassDef[_allClasses.size()];
        _allClasses.copyToFixed(array);

        Arrays.sort(array, 0, array.length, new Comparator<ClassDef>() {

            public int compare(ClassDef a, ClassDef b) {
                return a.Name.compareTo(b.Name);
            }
        });

        _allClasses.clear();
        _allClasses.addAll(array);
    }

    private void gather(PackageDef p) {
        if (_allPackages.contains(p))
            throw new RuntimeException("Package \"" + p + "\" is present in two packages.");

        _allPackages.add(p);

        for (int i = 0; i < p.Packages.size(); i++) {
            PackageDef child = p.Packages.get(i);
            child.setPackage(p);
            gather(child);
        }

        for (int i = 0; i < p.Classes.size(); i++) {
            ClassDef classDef = p.Classes.get(i);
            _allClasses.add(classDef);

            if (classDef.getPackage() != null && classDef.getPackage() != p)
                throw new RuntimeException("Class \"" + classDef + "\" is present in two packages.");

            classDef.setPackage(p);

            for (int t = 0; t < classDef.Methods.size(); t++)
                _allMethods.add(classDef.Methods.get(t));
        }
    }

    //

    public String toXMLString() {
        return toXMLString("http://objectfabric.org/schemas/" + GeneratorBase.SCHEMA_FILE);
    }

    public String toXMLString(String schema) {
        return toXMLString(schema, Target.JAVA);
    }

    public String toXMLString(String schema, Target target) {
        prepare();

        BeforeMarshall visitor = new BeforeMarshall(target);
        visitor.visit(this);

        return Platform.get().toXML(this, schema);
    }

    public static ObjectModelDef fromXMLFile(String file) {
        ObjectModelDef model = (ObjectModelDef) Platform.get().fromXMLFile(file);
        onRead(model);
        return model;
    }

    public static ObjectModelDef fromXMLString(String string) {
        ObjectModelDef model = (ObjectModelDef) Platform.get().fromXMLFile(string);
        onRead(model);
        return model;
    }

    private static void onRead(ObjectModelDef model) {
        model.prepare();

        AfterUnmarshall visitor = new AfterUnmarshall(model);
        visitor.visit(model);
    }
}
