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

import javax.xml.bind.annotation.XmlType;

@XmlType(name = "Type")
public class TypeDef {

    static {
        JVMPlatform.loadClass();
    }

    // TODO merge with TType

    static final String ENUM_VALUES_ARRAY = "_ENUM_VALUES_ARRAY";

    private List<TypeDef> _genericsParameters = new List<TypeDef>();

    private Immutable _immutable;

    private ClassDef _generated;

    private java.lang.Class _otherClass;

    private String _custom;

    private Immutable _customUnderlyingImmutable;

    public TypeDef() {
    }

    public TypeDef(Immutable immutable) {
        _immutable = immutable;
    }

    public TypeDef(ClassDef generated) {
        _generated = generated;
    }

    public TypeDef(java.lang.Class other) {
        Immutable immutable = Immutable.parse(Platform.get().simpleName(other));

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
            result._customUnderlyingImmutable = Immutable.parse(args.get(1));

            if (result._customUnderlyingImmutable == null)
                throw new IllegalArgumentException(text + ": Second parameter of a Custom type must be a primitive.");
        } else {
            result._immutable = Immutable.parse(name);

            if (result._immutable == null) {
                if ("object".equals(name) || "Object".equals(name) || "Object".equals(name) || "java.lang.Object".equals(name))
                    result._otherClass = Platform.get().objectClass();
                else if ("void".equals(name) || "Void".equals(name) || "Void".equals(name) || "java.lang.Void".equals(name))
                    result._otherClass = Platform.get().voidClass();
                else {
                    result._generated = findGeneratedClass(name, model);

                    if (result._generated == null) {
                        result._otherClass = findOtherClass(name);

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

    private static java.lang.Class findOtherClass(String name) {
        BuiltInClass builtIn = BuiltInClass.parse(name);
        java.lang.Class c = null;

        if (builtIn != null)
            c = Platform.get().defaultObjectModel().getClass(builtIn.id(), null);

        if (c != null)
            return c;

        c = PlatformGenerator.forName(name);

        if (c == null)
            c = PlatformGenerator.forName("org.objectfabric." + name);

        if (c != null)
            if (!(PlatformGenerator.isTObject(c)) && !PlatformGenerator.isJavaEnum(c))
                throw new IllegalArgumentException("Class \"" + c + "\" is not supported.");

        return c;
    }

    static ClassDef findGeneratedClass(String name, ObjectModelDef model) {
        for (int i = 0; i < model.allClasses().size(); i++) {
            ClassDef classDef = model.allClasses().get(i);

            if (classDef.fullName().equals(name) || classDef.Name.equals(name))
                return classDef;
        }

        return null;
    }

    public void addGenericsParameter(java.lang.Class c) {
        addGenericsParameter(new TypeDef(c));
    }

    public void addGenericsParameter(ClassDef classDef) {
        addGenericsParameter(new TypeDef(classDef));
    }

    public void addGenericsParameter(TypeDef type) {
        if (_genericsParameters == null)
            _genericsParameters = new List<TypeDef>();

        _genericsParameters.add(type);
    }

    String fullName(Target target) {
        return fullName(target, false);
    }

    String fullName(Target target, boolean generics) {
        return getFullName(target, generics, false);
    }

    String getFullName(Target target, boolean generics, boolean useParenthesis) {
        String s = className(target);

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

    String className(Target target) {
        if (_immutable != null)
            return target == Target.CSHARP ? _immutable.csharp() : _immutable.java();

        if (_generated != null)
            return _generated.fullName();

        if (_otherClass != null) {
            boolean ok = PlatformGenerator.isTObject(_otherClass);

            if (_otherClass == Platform.get().voidClass())
                ok = true;

            if (_otherClass == Platform.get().objectClass())
                ok = true;

            if (PlatformGenerator.isJavaEnum(_otherClass))
                ok = true;

            if (!ok)
                throw new IllegalArgumentException("Class \"" + Platform.get().name(_otherClass) + "\" is not supported.");

            return Platform.get().name(_otherClass).replace('$', '.');
        }

        if (_custom == null)
            throw new RuntimeException("Type value is missing.");

        return _custom;
    }

    String fullNameWithGenericsAndBoxPrimitives(Target target) {
        String result;

        if (target == Target.JAVA && immutable() != null && immutable().isPrimitive() && !immutable().isBoxed())
            result = immutable().boxed().java();
        else
            result = fullName(target, true);

        return result;
    }

    String cast(GeneratorBase generator) {
        String type = castType(generator);
        return type == null ? "" : "(" + type + ") ";
    }

    private String castType(GeneratorBase generator) {
        if (_otherClass == Platform.get().objectClass())
            return null;

        if (generator.isJava())
            if (_immutable != null && _immutable.isPrimitive() && !_immutable.isBoxed())
                return _immutable.boxed().java();

        return fullName(generator.target(), false);
    }

    String getTTypeString(Target target) {
        if (_immutable != null)
            return "org.objectfabric.Immutable." + Utils.getNameAsConstant(_immutable.toString()) + ".type()";

        if (_generated != null)
            return _generated.fullName() + ".TYPE";

        if (_otherClass != null) {
            String name = Platform.get().name(_otherClass).replace('$', '.');

            if (_otherClass == Platform.get().voidClass())
                return "org.objectfabric.TType.VOID";

            if (PlatformGenerator.isTObject(_otherClass))
                return name + ".TYPE";

            if (_otherClass == Platform.get().objectClass())
                return "org.objectfabric.TType.OBJECT";

            String type = target == Target.JAVA ? name + ".class" : "typeof(" + name + ")";
            return "new org.objectfabric.TType(" + type + ")";
        }

        if (_custom == null)
            throw new RuntimeException("Type value is missing.");

        String type = target == Target.JAVA ? _custom + ".class" : "typeof(" + _custom + ")";
        return "new org.objectfabric.TType(" + type + ")";
    }

    String defaultString() {
        return immutable() != null ? immutable().defaultString() : "null";
    }

    Immutable immutable() {
        return _immutable;
    }

    java.lang.Class otherClass() {
        return _otherClass;
    }

    String custom() {
        return _custom;
    }

    Immutable customUnderlyingImmutable() {
        return _customUnderlyingImmutable;
    }

    boolean isTObject() {
        return _generated != null || (_otherClass != null && PlatformGenerator.isTObject(_otherClass));
    }

    boolean canBeTObject() {
        if (isTObject())
            return true;

        if (_otherClass != null)
            if (_otherClass == Platform.get().objectClass() || PlatformGenerator.isInterface(_otherClass))
                return true;

        return false;
    }

    boolean isJavaEnum() {
        return _otherClass != null && PlatformGenerator.isJavaEnum(_otherClass);
    }

    ClassDef firstGeneratedClassAmongstThisAndParents(ObjectModelDef model) {
        if (_generated != null)
            return _generated;

        if (_otherClass != null) {
            java.lang.Class parent = Platform.get().superclass(_otherClass);

            while (parent != Platform.get().tObjectClass()) {
                ClassDef def = TypeDef.findGeneratedClass(Platform.get().name(parent), model);

                if (def != null)
                    return def;

                parent = Platform.get().superclass(parent);
            }
        }

        return null;
    }

    boolean isTGeneratedFields(ObjectModelDef model) {
        if (_generated != null && _generated.parent() != null)
            return _generated.parent().isTGeneratedFields(model);

        if (_otherClass != null) {
            java.lang.Class parent = Platform.get().superclass(_otherClass);

            while (parent != Platform.get().tObjectClass()) {
                if (parent == Platform.get().tGeneratedClass())
                    return true;

                parent = Platform.get().superclass(parent);
            }

            return false;
        }

        return true;
    }

    //

    boolean fixedLength() {
        if (immutable() != null && immutable().fixedLength() || isJavaEnum())
            return true;

        return false;
    }
}
