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

import java.util.Arrays;
import java.util.Comparator;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.objectfabric.generator.Visitor.AfterUnmarshall;
import com.objectfabric.generator.Visitor.BeforeMarshall;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformXML;

@XmlRootElement(name = "ObjectModel")
public class ObjectModelDef {

    @XmlAttribute
    public String Name = "ObjectModel";

    @XmlElement(name = "Package")
    public java.util.List<PackageDef> Packages = new List.FullImpl<PackageDef>();

    @XmlAttribute(required = false)
    public boolean Public = true;

    @XmlAttribute(required = false)
    public boolean Abstract;

    private final List<PackageDef> _allPackages = new List<PackageDef>();

    private final List<GeneratedClassDef> _allClasses = new List<GeneratedClassDef>();

    private final List<MethodDef> _allMethods = new List<MethodDef>();

    private List<String> _cachedClassNames;

    private List<String> _cachedUppercaseClassNames;

    private List<String> _cachedMethodIds;

    public ObjectModelDef() {
    }

    public ObjectModelDef(String name) {
        Name = name;
    }

    @XmlTransient
    final String getActualName() {
        return Name + (Abstract ? "Base" : "");
    }

    @XmlTransient
    final String getFullName() {
        return getRootPackage().getFullName() + "." + getActualName();
    }

    @XmlTransient
    final String getFullNameNonAbstract() {
        return getRootPackage().getFullName() + "." + Name;
    }

    final PackageDef getRootPackage() {
        if (Packages.size() == 0)
            throw new IllegalArgumentException("Object model needs to define at least one package.");

        return Packages.get(0);
    }

    @XmlTransient
    final List<PackageDef> getAllPackages() {
        return _allPackages;
    }

    @XmlTransient
    final List<GeneratedClassDef> getAllClasses() {
        return _allClasses;
    }

    @XmlTransient
    final List<MethodDef> getAllMethods() {
        return _allMethods;
    }

    @XmlTransient
    final List<String> getAllFullClassNames() {
        if (_cachedClassNames == null) {
            _cachedClassNames = new List<String>();
            List<GeneratedClassDef> temp = getAllClasses();

            for (int i = 0; i < temp.size(); i++)
                _cachedClassNames.add(temp.get(i).getFullName());
        }

        return _cachedClassNames;
    }

    @XmlTransient
    final List<String> getAllClassIds() {
        if (_cachedUppercaseClassNames == null) {
            _cachedUppercaseClassNames = new List<String>();
            List<String> temp = getAllFullClassNames();

            for (int i = 0; i < temp.size(); i++)
                _cachedUppercaseClassNames.add(formatAsConstant(temp.get(i)) + "_CLASS_ID");
        }

        return _cachedUppercaseClassNames;
    }

    @XmlTransient
    final List<String> getAllMethodIds() {
        if (_cachedMethodIds == null) {
            _cachedMethodIds = new List<String>();

            for (int i = 0; i < getAllMethods().size(); i++)
                _cachedMethodIds.add(formatAsConstant(getFullName()) + "_METHOD_" + i + "_ID");
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

        GeneratedClassDef[] array = new GeneratedClassDef[_allClasses.size()];
        _allClasses.copyToFixed(array);

        Arrays.sort(array, 0, array.length, new Comparator<GeneratedClassDef>() {

            public int compare(GeneratedClassDef a, GeneratedClassDef b) {
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
            GeneratedClassDef classDef = p.Classes.get(i);
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
        return toXMLString("http://objectfabric.com/schemas/" + Generator.SCHEMA_FILE);
    }

    public String toXMLString(String schema) {
        return toXMLString(schema, Target.JAVA);
    }

    public String toXMLString(String schema, Target target) {
        prepare();

        BeforeMarshall visitor = new BeforeMarshall(target);
        visitor.visit(this);

        return PlatformXML.toXMLString(this, schema);
    }

    public static ObjectModelDef fromXMLFile(String file) {
        ObjectModelDef model = PlatformXML.fromXMLFile(file);
        onRead(model);
        return model;
    }

    public static ObjectModelDef fromXMLString(String xml) {
        ObjectModelDef model = PlatformXML.fromXMLString(xml);
        onRead(model);
        return model;
    }

    private static void onRead(ObjectModelDef model) {
        model.prepare();

        AfterUnmarshall visitor = new AfterUnmarshall(model);
        visitor.visit(model);
    }
}
