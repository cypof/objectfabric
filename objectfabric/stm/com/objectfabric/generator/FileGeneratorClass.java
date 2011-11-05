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

import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformClass;

class FileGeneratorClass extends FileGenerator {

    protected final GeneratedClassDef _classDef;

    public FileGeneratorClass(Generator generator, GeneratedClassDef classDef) {
        super(generator, classDef.getPackage().getFullName(), classDef.getActualName(generator.getObjectModel()));

        _classDef = classDef;
    }

    protected FileGeneratorValueSet createDataSetWriter() {
        return new FileGeneratorValueSet(g(), _classDef, this);
    }

    @Override
    protected String onLine(String line) {
        return replace(line, g().isCSharp());
    }

    public static String replace(String line, boolean cs) {
        if (cs) {
            line = line.replace("Transaction.getCurrent()", "Transaction.Current");
            line = line.replace("com.objectfabric.TObject.Version", "Version");
            line = line.replace("com.objectfabric.misc", "ObjectFabric");
            line = line.replace("com.objectfabric", "ObjectFabric");
            line = line.replace(" static final ", " const ");
            line = line.replace("java.lang.Object", "object");
            line = line.replace("java.lang.String", "string");
            line = line.replace("java.lang.Exception", "System.Exception");
            line = line.replace("IllegalArgumentException", "ArgumentException");
            line = line.replace("IllegalStateException", "InvalidOperationException");
        }

        return line;
    }

    @Override
    protected void header() {
        copyright();
        wl();
        boolean cSharp = g().isCSharp();

        if (cSharp) {
            wl("using System;");
            wl();
            warning();
            wl();
            wl("namespace " + Package);
            wl("{");

            tab();
        } else {
            wl("package " + Package + ";");
            wl();
            warning();
        }

        String ext;
        ObjectModelDef model = g().getObjectModel();

        if (_classDef.getParent() != null)
            ext = _classDef.getParent().getFullName(g().getTarget(), true);
        else {
            if (_classDef.lessOr32Fields(model))
                ext = "com.objectfabric.TGeneratedFields32";
            else
                ext = "com.objectfabric.TGeneratedFieldsN";
        }

        if (_classDef.Comment != null && _classDef.Comment.length() > 0)
            wl("    /** " + _classDef.Comment + " */");

        String public_ = _classDef.Public ? "public " : "";
        String abstract_ = _classDef.Abstract ? "abstract " : "";
        String partial = g().isCSharp() ? "partial " : "";

        StringBuilder args = new StringBuilder();
        StringBuilder argsNoCollections = new StringBuilder();
        StringBuilder argsNames = new StringBuilder();
        StringBuilder argsNamesNoCollections = new StringBuilder();
        StringBuilder argsNamesWithCollectionConstructors = new StringBuilder();
        List<ValueDef> allValues = _classDef.getAllValues(model);

        for (int i = 0; i < allValues.size(); i++) {
            ValueDef value = allValues.get(i);

            if (value.isReadOnly()) {
                if (args.length() > 0) {
                    args.append(", ");
                    argsNames.append(", ");
                }

                String type = value.getType().getFullName(g().getTarget(), true);
                args.append(type + " " + value.Name);
                argsNames.append(value.Name);

                if (!PlatformClass.isCollection(value.getType().getOtherClass())) {
                    if (argsNoCollections.length() > 0)
                        argsNoCollections.append(", ");

                    if (argsNamesNoCollections.length() > 0) {
                        argsNamesNoCollections.append(", ");
                        argsNamesWithCollectionConstructors.append(", ");
                    }

                    argsNoCollections.append(type + " " + value.Name);
                    argsNamesNoCollections.append(value.Name);
                    argsNamesWithCollectionConstructors.append(value.Name);
                } else {
                    if (argsNamesWithCollectionConstructors.length() > 0)
                        argsNamesWithCollectionConstructors.append(", ");

                    argsNamesWithCollectionConstructors.append("new " + type + "(trunk)");
                }
            }
        }

        String commaArgs = args.length() > 0 ? ", " + args : "";
        String commaArgsNames = argsNames.length() > 0 ? ", " + argsNames : "";
        String commaArgsNoCollections = argsNoCollections.length() > 0 ? ", " + argsNoCollections : "";
        String commaArgsNamesNoCollections = argsNamesNoCollections.length() > 0 ? ", " + argsNamesNoCollections : "";
        String commaArgsNamesWithCollectionConstructors = argsNamesWithCollectionConstructors.length() > 0 ? ", " + argsNamesWithCollectionConstructors : "";
        StringBuilder parentArgsNames = new StringBuilder();
        GeneratedClassDef parent = _classDef.getParentGeneratedClass(model);
        String ctorVisibility = _classDef.Abstract ? "protected" : "public";

        if (parent != null) {
            List<ValueDef> parentValues = parent.getAllValues(model);

            for (int i = 0; i < parentValues.size(); i++)
                if (parentValues.get(i).isReadOnly())
                    parentArgsNames.append(", " + parentValues.get(i).Name);
        }

        if (g().isJava())
            wl("@SuppressWarnings({ \"hiding\", \"unchecked\", \"static-access\", \"unused\" })");

        wl(public_ + abstract_ + partial + "class " + Name + " " + g().getTarget().extendsString() + " " + ext + " {");

        if (_classDef.isTGeneratedFields(g().getObjectModel())) {
            /*
             * Create collections if field is read only.
             */
            if (args.length() != argsNoCollections.length()) {
                wl();
                wl("    " + ctorVisibility + " " + Name + "(" + argsNoCollections + ")" + (cSharp ? "" : " {"));

                if (cSharp) {
                    wl("        : this(ObjectFabric.Transaction.DefaultTrunk" + commaArgsNamesNoCollections + ")");
                    wl("    {");
                } else
                    wl("        this(com.objectfabric.Transaction.getDefaultTrunk()" + commaArgsNamesNoCollections + ");");

                wl("    }");
                wl();
                wl("    " + ctorVisibility + " " + Name + "(com.objectfabric.Transaction trunk" + commaArgsNoCollections + ")" + (cSharp ? "" : " {"));

                if (cSharp) {
                    wl("        : this(trunk" + commaArgsNamesWithCollectionConstructors + ")");
                    wl("    {");
                } else
                    wl("        this(trunk" + commaArgsNamesWithCollectionConstructors + ");");

                wl("    }");
            }

            wl();
            wl("    " + ctorVisibility + " " + Name + "(" + args + ")" + (cSharp ? "" : " {"));

            if (cSharp) {
                wl("        : this(ObjectFabric.Transaction.DefaultTrunk" + commaArgsNames + ")");
                wl("    {");
            } else
                wl("        this(com.objectfabric.Transaction.getDefaultTrunk()" + commaArgsNames + ");");

            wl("    }");
            wl();
            wl("    " + ctorVisibility + " " + Name + "(com.objectfabric.Transaction trunk" + commaArgs + ")" + (cSharp ? "" : " {"));

            if (cSharp) {
                wl("        : this(new Version(null, FIELD_COUNT), trunk" + commaArgsNames + ")");
                wl("    {");
            } else
                wl("        this(new Version(null, FIELD_COUNT), trunk" + commaArgsNames + ");");

            wl("    }");
        }

        wl();
        wl("    protected " + Name + "(com.objectfabric.TObject.Version shared, com.objectfabric.Transaction trunk" + commaArgs + ")" + (cSharp ? "" : " {"));

        if (cSharp) {
            wl("        : base(shared, trunk" + parentArgsNames + ")");
            wl("    {");
        } else
            wl("        super(shared, trunk" + parentArgsNames + ");");

        List<ValueDef> values = _classDef.getValues();

        for (int i = 0; i < values.size(); i++) {
            ValueDef value = values.get(i);

            if (value.isReadOnly()) {
                wl();

                if (value.getType().getOtherClass() == PlatformClass.getObjectClass())
                    wl("        ((Version) shared)._" + value.Name + " = shared.mergeObject(((Version) shared)._" + value.Name + ", " + value.Name + ");");
                if (value.getType().isTObject())
                    wl("        ((Version) shared)._" + value.Name + " = shared.mergeTObject(((Version) shared)._" + value.Name + ", " + value.Name + ");");
                else
                    wl("        ((Version) shared)._" + value.Name + " = " + value.Name + ";");

                wl();
                wl("        if (" + value.Name + " != " + value.getType().getDefaultString() + ")");
                wl("            ((Version) shared).setBit(" + value.getNameAsConstant() + "_INDEX);");
            }
        }

        wl("    }");
    }

    @Override
    protected void body() {
        FileGeneratorValueSet dsWriter = createDataSetWriter();

        if (!"Transaction".equals(_classDef.Name))
            dsWriter.writeType();

        if (_classDef.isTGeneratedFields(g().getObjectModel())) {
            dsWriter.writeFields();
            dsWriter.writeFieldsConstantsAndCount();
        }

        if (_classDef.Methods.size() > 0) {
            _classDef.computeMethodIndexesInClass(g().getObjectModel());

            wl();
            wl("    // Methods");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = "METHOD_" + method.getIndexInClass();
                wl();
                wl("    protected static final int " + name + " = " + method.getIndexInClass() + ";");
            }

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                writer.writeCalls();
                wl();
            }

            if (g().isJava()) {
                wl("    @SuppressWarnings({ \"static-access\", \"cast\" })");
                wl("    @Override");
            }

            wl("    protected" + g().getTarget().overrideString() + "void invoke_objectfabric(com.objectfabric.MethodCall call) {");
            wl("        switch (getMethodCallIndex_objectfabric(call)) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = "METHOD_" + method.getIndexInClass();
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                wl("            case " + name + ": {");
                writer.writeInvocation();
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().getTarget().superString() + ".invoke_objectfabric(call);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava()) {
                wl("    @SuppressWarnings({ \"static-access\", \"cast\" })");
                wl("    @Override");
            }

            wl("    protected" + g().getTarget().overrideString() + "void setResult_objectfabric(com.objectfabric.TObject.Version version, int index, java.lang.Object result) {");
            wl("        switch (index) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                wl("            case METHOD_" + method.getIndexInClass() + ": {");

                if (method.ReturnValue.getType().getOtherClass() != PlatformClass.getVoidClass()) {
                    String name = method.getFullType(g());
                    wl("                ((" + name + ".Version) version)._return_objectfabric = (" + method.ReturnValue.getType().getFullNameWithGenericsAndBoxPrimitives(g().getTarget()) + ") result;");
                    wl("                ((" + name + ".Version) version).setBit(" + name + ".RETURN_OBJECTFABRIC_INDEX);");
                }

                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().getTarget().superString() + ".setResult_objectfabric(version, index, result);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava()) {
                wl("    @SuppressWarnings(\"static-access\")");
                wl("    @Override");
            }

            wl("    protected" + g().getTarget().overrideString() + "void setError_objectfabric(com.objectfabric.TObject.Version version, int index, java.lang.String error) {");
            wl("        switch (index) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = method.getFullType(g());
                wl("            case METHOD_" + method.getIndexInClass() + ": {");
                wl("                ((" + name + ".Version) version)._error_objectfabric = error;");
                wl("                ((" + name + ".Version) version).setBit(" + name + ".ERROR_OBJECTFABRIC_INDEX);");
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().getTarget().superString() + ".setError_objectfabric(version, index, error);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava()) {
                wl("    @SuppressWarnings(\"static-access\")");
                wl("    @Override");
            }

            wl("    protected" + g().getTarget().overrideString() + "void getResultOrError_objectfabric(com.objectfabric.MethodCall call) {");
            wl("        switch (getMethodCallIndex_objectfabric(call)) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                wl("            case METHOD_" + method.getIndexInClass() + ": {");
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                writer.writeResult();
                wl();
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().getTarget().superString() + ".getResultOrError_objectfabric(call);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
        }

        if (_classDef.isTGeneratedFields(g().getObjectModel()))
            dsWriter.writeVersion();

        wl("}");
    }

    @Override
    protected void footer() {
        if (g().isCSharp()) {
            untab();

            wl("}");
        }

        super.footer();
    }
}
