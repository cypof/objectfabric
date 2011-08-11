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

@XmlType(name = "Package")
public class PackageDef {

    @XmlAttribute
    public String Name;

    @XmlElement(name = "Package")
    public java.util.List<PackageDef> Packages = new List.FullImpl<PackageDef>();

    @XmlElement(name = "Class")
    public java.util.List<GeneratedClassDef> Classes = new List.FullImpl<GeneratedClassDef>();

    private PackageDef _package;

    public PackageDef() {
    }

    public PackageDef(String name) {
        Name = name;
    }

    @XmlTransient
    PackageDef getPackage() {
        return _package;
    }

    void setPackage(PackageDef value) {
        _package = value;
    }

    @XmlTransient
    String getFullName() {
        String s = "";

        if (_package != null)
            s += _package.getFullName() + ".";

        return s + Name;
    }
}
