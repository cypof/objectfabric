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

import javax.xml.bind.annotation.XmlType;

import com.objectfabric.ImmutableClass;
import com.objectfabric.Privileged;
import com.objectfabric.TKeyed;
import com.objectfabric.TList;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformClass;

@XmlType(name = "Type")
public class TypeDef extends Privileged {

    static final String ENUM_VALUES_ARRAY = "_ENUM_VALUES_ARRAY";

    private List<TypeDef> _genericsParameters = new List<TypeDef>();

    private ImmutableClass _immutable;

    private GeneratedClassDef _classDef;

    private java.lang.Class _otherClass;

    private String _custom;

    private ImmutableClass _customUnderlyingImmutable;

    public TypeDef() {
    }

    public TypeDef(ImmutableClass immutable) {
        _immutable = immutable;
    }

    public TypeDef(GeneratedClassDef classDef) {
        _classDef = classDef;
    }

    public TypeDef(java.lang.Class other) {
        ImmutableClass immutable = ImmutableClass.parse(PlatformClass.getSimpleName(other));

        if (immutable != null)
            _immutable = immutable;
        else
            _otherClass = other;
    }

    static TypeDef parse(String text, ObjectModelDef model) {
        if (text == null)
            throw new IllegalArgumentException("Type is null.");

        TypeDef result = new TypeDef();
        List<String> args = new List<String>();
        String name = text;
        StringBuilder sb = new StringBuilder();
        int parenthesisLevel = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '(':
                    if (parenthesisLevel == 0) {
                        name = sb.toString().trim();
                        sb.setLength(0);
                    } else
                        sb.append(c);

                    parenthesisLevel++;
                    break;

                case ')':
                    if (parenthesisLevel == 1) {
                        args.add(sb.toString().trim());
                        sb.setLength(0);
                    } else
                        sb.append(c);

                    parenthesisLevel--;
                    break;

                case ',':
                    if (parenthesisLevel == 1) {
                        args.add(sb.toString().trim());
                        sb.setLength(0);
                    }

                    break;

                default:
                    sb.append(c);
                    break;
            }
        }

        if ("Custom".equals(name)) {
            if (args.size() != 2)
                throw new IllegalArgumentException(text + ": Custom type requires 2 parameters, e.g. Custom(MyEnum, int).");

            result._custom = args.get(0);
            result._customUnderlyingImmutable = ImmutableClass.parse(args.get(1));

            if (result._customUnderlyingImmutable == null)
                throw new IllegalArgumentException(text + ": Second parameter of a Custom type must be a primitive.");
        } else {
            result._immutable = ImmutableClass.parse(name);

            if (result._immutable == null) {
                if ("object".equals(name) || "Object".equals(name) || "Object".equals(name) || "java.lang.Object".equals(name))
                    result._otherClass = PlatformClass.getObjectClass();
                else if ("void".equals(name) || "Void".equals(name) || "Void".equals(name) || "java.lang.Void".equals(name))
                    result._otherClass = PlatformClass.getVoidClass();
                else {
                    result._classDef = findGeneratedClass(name, model);

                    if (result._classDef == null) {
                        result._otherClass = findOtherClass(name, model);

                        if (result._otherClass == null)
                            throw new IllegalArgumentException(name);
                    }
                }
            }

            for (int i = 0; i < args.size(); i++)
                result._genericsParameters.add(parse(args.get(i), model));
        }

        return result;
    }

    private static java.lang.Class findOtherClass(String name, ObjectModelDef model) {
        java.lang.Class c = getBuiltInClassFromString(name);

        if (c != null)
            return c;

        c = PlatformClass.forName(name);

        if (c == null)
            c = PlatformClass.forName("com.objectfabric." + name);

        if (c != null)
            if (!(PlatformClass.isTObject(c)) && !PlatformClass.isJavaEnum(c))
                throw new IllegalArgumentException("Class \"" + c + "\" is not supported.");

        return c;
    }

    static GeneratedClassDef findGeneratedClass(String name, ObjectModelDef model) {
        for (int i = 0; i < model.getAllClasses().size(); i++) {
            GeneratedClassDef classDef = model.getAllClasses().get(i);

            if (classDef.getFullName().equals(name) || classDef.Name.equals(name))
                return classDef;
        }

        return null;
    }

    public void addGenericsParameter(java.lang.Class c) {
        addGenericsParameter(new TypeDef(c));
    }

    public void addGenericsParameter(GeneratedClassDef classDef) {
        addGenericsParameter(new TypeDef(classDef));
    }

    public void addGenericsParameter(TypeDef type) {
        if (_genericsParameters == null)
            _genericsParameters = new List<TypeDef>();

        _genericsParameters.add(type);
    }

    String getFullName(Target target) {
        return getFullName(target, false);
    }

    String getFullName(Target target, boolean generics) {
        return getFullName(target, generics, false);
    }

    String getFullName(Target target, boolean generics, boolean useParenthesis) {
        String s = getClassName(target);

        if (target == Target.CSHARP || generics) {
            if (_genericsParameters != null && _genericsParameters.size() > 0) {
                String params = "";

                for (int i = 0; i < _genericsParameters.size(); i++) {
                    if (params.length() != 0)
                        params += ", ";

                    params += _genericsParameters.get(i).getFullName(target, generics, useParenthesis);
                }

                if (useParenthesis)
                    s += "(" + params + ")";
                else
                    s += "<" + params + ">";
            }
        }

        return s;

    }

    String getClassName(Target target) {
        if (_immutable != null)
            return target == Target.CSHARP ? _immutable.getCSharp() : _immutable.getJava();

        if (_classDef != null)
            return _classDef.getFullName();

        if (_otherClass != null) {
            boolean ok = PlatformClass.isTObject(_otherClass);

            if (_otherClass == PlatformClass.getVoidClass())
                ok = true;

            if (_otherClass == PlatformClass.getObjectClass())
                ok = true;

            if (PlatformClass.isJavaEnum(_otherClass))
                ok = true;

            if (!ok)
                throw new IllegalArgumentException("Class \"" + PlatformClass.getName(_otherClass) + "\" is not supported.");

            return PlatformClass.getName(_otherClass).replace('$', '.');
        }

        if (_custom == null)
            throw new RuntimeException("Type value is missing.");

        return _custom;
    }

    String getFullNameWithGenericsAndBoxPrimitives(Target target) {
        String fullName;
        boolean java = target == Target.JAVA || target == Target.GWT;

        if (java && getImmutable() != null && getImmutable().isPrimitive() && !getImmutable().isBoxed())
            fullName = getImmutable().getBoxed().getJava();
        else
            fullName = getFullName(target, true);

        return fullName;
    }

    String getCast(Generator generator) {
        String type = getCastType(generator);
        return type == null ? "" : "(" + type + ") ";
    }

    private String getCastType(Generator generator) {
        if (_otherClass == PlatformClass.getObjectClass())
            return null;

        if (generator.isJava())
            if (_immutable != null && _immutable.isPrimitive() && !_immutable.isBoxed())
                return _immutable.getBoxed().getJava();

        return getFullName(generator.getTarget(), false);
    }

    String getDefaultString() {
        return getImmutable() != null ? getImmutable().getDefaultString() : "null";
    }

    ImmutableClass getImmutable() {
        return _immutable;
    }

    java.lang.Class getOtherClass() {
        return _otherClass;
    }

    String getCustom() {
        return _custom;
    }

    ImmutableClass getCustomUnderlyingImmutable() {
        return _customUnderlyingImmutable;
    }

    boolean isTObject() {
        return _classDef != null || (_otherClass != null && PlatformClass.isTObject(_otherClass));
    }

    boolean isJavaEnum() {
        return _otherClass != null && PlatformClass.isJavaEnum(_otherClass);
    }

    @SuppressWarnings("unchecked")
    boolean isCollection() {
        if (_otherClass != null)
            for (Class c : new Class[] { TList.class, TKeyed.class })
                if (c.isAssignableFrom(_otherClass))
                    return true;

        return false;
    }

    public GeneratedClassDef getFirstGeneratedClassAmongstThisAndParents(ObjectModelDef model) {
        if (_classDef != null)
            return _classDef;

        if (_otherClass != null) {
            java.lang.Class parent = PlatformClass.getSuperclass(_otherClass);

            while (parent != PlatformClass.getTObjectClass()) {
                GeneratedClassDef def = TypeDef.findGeneratedClass(PlatformClass.getName(parent), model);

                if (def != null)
                    return def;

                parent = PlatformClass.getSuperclass(parent);
            }
        }

        return null;
    }

    public boolean isTGeneratedFields(ObjectModelDef model) {
        if (_classDef != null && _classDef.getParent() != null)
            return _classDef.getParent().isTGeneratedFields(model);

        if (_otherClass != null) {
            java.lang.Class parent = PlatformClass.getSuperclass(_otherClass);

            while (parent != PlatformClass.getTObjectClass()) {
                if (parent == PlatformClass.getTGeneratedFieldsClass())
                    return true;

                parent = PlatformClass.getSuperclass(parent);
            }

            return false;
        }

        return true;
    }

    //

    boolean fixedLength() {
        if (getImmutable() != null && !getImmutable().fixedLength())
            return false;

        if (isTObject())
            return false;

        if (getOtherClass() == PlatformClass.getObjectClass())
            return false;

        return true;
    }
}
