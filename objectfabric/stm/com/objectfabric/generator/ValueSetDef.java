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

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.PlatformSet;

public abstract class ValueSetDef {

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

    abstract String getActualName(ObjectModelDef model);

    abstract TypeDef getParent();

    abstract GeneratedClassDef getParentGeneratedClass(ObjectModelDef model);

    abstract List<ValueDef> createValues();

    List<ValueDef> getValues() {
        if (_values == null)
            _values = createValues();

        return _values;
    }

    ValueDef getValue(int index) {
        return getValues().get(index);
    }

    List<ValueDef> getAllValues(ObjectModelDef model) {
        if (_allValues == null) {
            _allValues = new List<ValueDef>();

            GeneratedClassDef parent = getParentGeneratedClass(model);

            if (parent != null) {
                List<ValueDef> temp = parent.getAllValues(model);

                for (int i = 0; i < temp.size(); i++)
                    _allValues.add(temp.get(i));
            }

            List<ValueDef> temp = getValues();

            for (int i = 0; i < temp.size(); i++)
                _allValues.add(temp.get(i));
        }

        return _allValues;
    }

    final Iterable<TypeDef> getEnums() {
        return _enums;
    }

    final void registerEnum(TypeDef type) {
        if (Debug.ENABLED)
            Debug.assertion(PlatformClass.isJavaEnum(type.getOtherClass()));

        _enums.add(type);
    }

    /**
     * @param model
     */
    boolean lessOr32Fields(ObjectModelDef model) {
        return getValues().size() <= 32;
    }
}
