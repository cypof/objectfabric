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

public abstract class ValueSetDef {

    static {
        JVMPlatform.loadClass();
    }

    @XmlAttribute
    public String Name;

    @XmlAttribute(required = false)
    public boolean Public = true;

    @XmlAttribute(required = false)
    public String Comment;

    private List<ValueDef> _values;

    private List<ValueDef> _allValues;

    private final PlatformSet<TypeDef> _enums = new PlatformSet<TypeDef>();

    public ValueSetDef() {
    }

    public ValueSetDef(String name, String comment) {
        Name = name;
        Comment = comment;
    }

    abstract String actualName(ObjectModelDef model);

    abstract TypeDef parent();

    abstract ClassDef parentGeneratedClass(ObjectModelDef model);

    abstract List<ValueDef> createValues();

    List<ValueDef> values() {
        if (_values == null)
            _values = createValues();

        return _values;
    }

    ValueDef value(int index) {
        return values().get(index);
    }

    List<ValueDef> allValues(ObjectModelDef model) {
        if (_allValues == null) {
            _allValues = new List<ValueDef>();

            ClassDef parent = parentGeneratedClass(model);

            if (parent != null) {
                List<ValueDef> temp = parent.allValues(model);

                for (int i = 0; i < temp.size(); i++)
                    _allValues.add(temp.get(i));
            }

            List<ValueDef> temp = values();

            for (int i = 0; i < temp.size(); i++)
                _allValues.add(temp.get(i));
        }

        return _allValues;
    }

    final Iterable<TypeDef> enums() {
        return _enums;
    }

    final void registerEnum(TypeDef type) {
        if (Debug.ENABLED)
            Debug.assertion(PlatformGenerator.isJavaEnum(type.otherClass()));

        _enums.add(type);
    }

    /**
     * @param model
     */
    boolean lessOr32Fields(ObjectModelDef model) {
        return values().size() <= 32;
    }
}
