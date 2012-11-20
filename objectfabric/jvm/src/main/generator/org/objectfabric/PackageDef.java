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
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "Package")
public class PackageDef {

    static {
        JVMPlatform.loadClass();
    }

    @XmlAttribute
    public String Name;

    @XmlElement(name = "Package")
    public java.util.List<PackageDef> Packages = new java.util.ArrayList<PackageDef>();

    @XmlElement(name = "Class")
    public java.util.List<ClassDef> Classes = new java.util.ArrayList<ClassDef>();

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

    String fullName() {
        String s = "";

        if (_package != null)
            s += _package.fullName() + ".";

        return s + Name;
    }
}
