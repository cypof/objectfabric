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

class FileGeneratorClass extends FileGenerator {

    private final ClassDef _classDef;

    FileGeneratorClass(GeneratorBase generator, ClassDef classDef) {
        super(generator, classDef.getPackage().fullName(), classDef.actualName(generator.objectModel()));

        _classDef = classDef;
    }

    FileGeneratorValueSet createDataSetWriter() {
        return new FileGeneratorValueSet(g(), _classDef, this);
    }

    @Override
    String onLine(String line) {
        return replace(line, g().isCSharp());
    }

    static String replace(String line, boolean cs) {
        if (cs) {
            line = line.replace("Transaction.getCurrent()", "Transaction.Current");
            line = line.replace("org.objectfabric.TObject.Version", "Version");
            line = line.replace("org.objectfabric", "ObjectFabric");
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
    void header() {
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
        ObjectModelDef model = g().objectModel();

        if (_classDef.parent() != null)
            ext = _classDef.parent().fullName(g().target(), true);
        else
            ext = "org.objectfabric.TGenerated";

        if (_classDef.Comment != null && _classDef.Comment.length() > 0)
            wl("    /** " + _classDef.Comment + " */");

        String public_ = _classDef.Public ? "public " : "";
        String abstract_ = _classDef.getAbstract() ? "abstract " : "";
        String partial = g().isCSharp() ? "partial " : "";
        StringBuilder args = new StringBuilder();
        StringBuilder argsNoCollections = new StringBuilder();
        StringBuilder argsNames = new StringBuilder();
        StringBuilder argsNamesNoCollections = new StringBuilder();
        StringBuilder argsNamesWithCollectionConstructors = new StringBuilder();
        List<ValueDef> allValues = _classDef.allValues(model);

        for (int i = 0; i < allValues.size(); i++) {
            ValueDef value = allValues.get(i);

            if (value.isReadOnly()) {
                if (args.length() > 0) {
                    args.append(", ");
                    argsNames.append(", ");
                }

                String type = value.type().fullName(g().target(), true);
                args.append(type + " " + value.Name);
                argsNames.append(value.Name);
                java.lang.Class other = value.type().otherClass();

                if (other == null || !PlatformGenerator.isCollection(other)) {
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

                    argsNamesWithCollectionConstructors.append("new " + type + "(resource)");
                }
            }
        }

        String commaArgs = args.length() > 0 ? ", " + args : "";
        String commaArgsNames = argsNames.length() > 0 ? ", " + argsNames : "";
        String commaArgsNoCollections = argsNoCollections.length() > 0 ? ", " + argsNoCollections : "";
        String commaArgsNamesWithCollectionConstructors = argsNamesWithCollectionConstructors.length() > 0 ? ", " + argsNamesWithCollectionConstructors : "";
        StringBuilder parentArgsNames = new StringBuilder();
        ClassDef parent = _classDef.parentGeneratedClass(model);
        String ctorVisibility = _classDef.getAbstract() ? "protected" : "public";

        if (parent != null) {
            List<ValueDef> parentValues = parent.allValues(model);

            for (int i = 0; i < parentValues.size(); i++)
                if (parentValues.get(i).isReadOnly())
                    parentArgsNames.append(", " + parentValues.get(i).Name);
        }

        if (g().isJava())
            wl("@SuppressWarnings({ \"hiding\", \"unchecked\", \"static-access\", \"unused\", \"cast\", \"rawtypes\" })");

        wl(public_ + abstract_ + partial + "class " + Name + " " + g().target().extendsString() + " " + ext + " {");

        if (!_classDef.getAbstract() && _classDef.isTGeneratedFields(g().objectModel())) {
            /*
             * Create collections if field is read only.
             */
            if (args.length() != argsNoCollections.length()) {
                wl();
                wl("    " + ctorVisibility + " " + Name + "(org.objectfabric.Resource resource" + commaArgsNoCollections + ")" + (cSharp ? "" : " {"));

                if (cSharp) {
                    wl("        : this(resource" + commaArgsNamesWithCollectionConstructors + ")");
                    wl("    {");
                } else
                    wl("        this(resource" + commaArgsNamesWithCollectionConstructors + ");");

                wl("    }");
            }

            wl();
            wl("    " + ctorVisibility + " " + Name + "(org.objectfabric.Resource resource" + commaArgs + ")" + (cSharp ? "" : " {"));

            if (cSharp) {
                wl("        : this(resource, new Version(FIELD_COUNT), FIELD_COUNT" + commaArgsNames + ")");
                wl("    {");
            } else
                wl("        this(resource, new Version(FIELD_COUNT), FIELD_COUNT" + commaArgsNames + ");");

            wl("    }");
        }

        wl();
        wl("    protected " + Name + "(org.objectfabric.Resource resource, org.objectfabric.TObject.Version shared, int length" + commaArgs + ")" + (cSharp ? "" : " {"));

        if (cSharp) {
            wl("        : base(resource, shared, FIELD_COUNT" + parentArgsNames + ")");
            wl("    {");
        } else
            wl("        super(resource, shared, FIELD_COUNT" + parentArgsNames + ");");

        List<ValueDef> values = _classDef.values();

        for (int i = 0; i < values.size(); i++) {
            ValueDef value = values.get(i);

            if (value.isReadOnly()) {
                wl();
                wl("        ((Version) shared)._" + value.Name + " = " + value.Name + ";");
                wl();
                wl("        if (" + value.Name + " != " + value.type().defaultString() + ")");
                wl("            ((Version) shared).setBit(" + value.nameAsConstant() + "_INDEX);");
            }
        }

        StringBuilder gets = new StringBuilder();

        for (int i = 0; i < allValues.size(); i++) {
            ValueDef value = allValues.get(i);

            if (value.isReadOnly())
                gets.append(", toCopy." + value.Name + "()");
        }

        wl("    }");

        if (!_classDef.getAbstract()) {
            wl();
            wl("    public " + Name + "(" + Name + " toCopy)" + (cSharp ? "" : " {"));

            if (cSharp) {
                wl("        : this(toCopy.resource()" + gets + ")");
                wl("    {");
            } else
                wl("        this(toCopy.resource()" + gets + ");");

            wl();

            for (int i = 0; i < allValues.size(); i++) {
                ValueDef value = allValues.get(i);

                if (!value.isReadOnly()) {
                    String name = Utils.getWithFirstLetterUp(value.Name);

                    if (g().addGetAndSet())
                        wl("        set" + name + "(toCopy.get" + name + "());");
                    else
                        wl("        " + value.Name + "(toCopy." + value.Name + "());");
                }
            }

            wl("    }");
        }
    }

    @Override
    void body() {
        FileGeneratorValueSet dsWriter = createDataSetWriter();

        if (!_classDef.getAbstract())
            dsWriter.writeType();

        if (_classDef.isTGeneratedFields(g().objectModel())) {
            dsWriter.writeFields();
            dsWriter.writeFieldsConstantsAndCount();
        }

        if (_classDef.Methods.size() > 0) {
            _classDef.computeMethodIndexesInClass(g().objectModel());

            wl();
            wl("    // Methods");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = "METHOD_" + method.indexInClass();
                wl();
                wl("    protected static final int " + name + " = " + method.indexInClass() + ";");
            }

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                writer.writeCalls();
                wl();
            }

            if (g().isJava())
                wl("    @Override");

            wl("    protected" + g().target().overrideString() + "void invoke_(org.objectfabric.MethodCall call) {");
            wl("        switch (getMethodCallIndex_(call)) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = "METHOD_" + method.indexInClass();
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                wl("            case " + name + ": {");
                writer.writeInvocation();
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().target().superString() + ".invoke_(call);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava())
                wl("    @Override");

            wl("    protected" + g().target().overrideString() + "void setResult_(org.objectfabric.TObject.Version version, int index, java.lang.Object result) {");
            wl("        switch (index) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = method.fullType(g());
                wl("            case METHOD_" + method.indexInClass() + ": {");
                wl("                ((" + name + ".Version) version).clearBits();");

                if (method.ReturnValue.type().otherClass() != Platform.get().voidClass()) {

                    wl("                ((" + name + ".Version) version).__return = (" + method.ReturnValue.type().fullNameWithGenericsAndBoxPrimitives(g().target()) + ") result;");
                    wl("                ((" + name + ".Version) version).setBit(" + name + "._RETURN_INDEX);");
                }

                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().target().superString() + ".setResult_(version, index, result);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava())
                wl("    @Override");

            wl("    protected" + g().target().overrideString() + "void setError_(org.objectfabric.TObject.Version version, int index, java.lang.String error) {");
            wl("        switch (index) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                String name = method.fullType(g());
                wl("            case METHOD_" + method.indexInClass() + ": {");
                wl("                ((" + name + ".Version) version).clearBits();");
                wl("                ((" + name + ".Version) version).__error = error;");
                wl("                ((" + name + ".Version) version).setBit(" + name + "._ERROR_INDEX);");
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().target().superString() + ".setError_(version, index, error);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
            wl();

            if (g().isJava())
                wl("    @Override");

            wl("    protected" + g().target().overrideString() + "void getResultOrError_(org.objectfabric.MethodCall call) {");
            wl("        switch (getMethodCallIndex_(call)) {");

            for (int i = 0; i < _classDef.Methods.size(); i++) {
                MethodDef method = _classDef.Methods.get(i);
                wl("            case METHOD_" + method.indexInClass() + ": {");
                FileGeneratorMethod writer = new FileGeneratorMethod(this, method);
                writer.writeResult();
                wl();
                wl("                break;");
                wl("            }");
            }

            wl("            default: {");
            wl("                " + g().target().superString() + ".getResultOrError_(call);");
            wl("                break;");
            wl("            }");
            wl("        }");
            wl("    }");
        }

        if (!_classDef.getAbstract()) {
            wl();

            if (g().isJava()) {
                wl("    @Override");
                wl("    protected org.objectfabric.TObject.Version createVersion_() {");
                wl("        Version version = new Version(0);");
                wl("        version.setObject(this);");
                wl("        return version;");
                wl("    }");
            } else {
                wl("    protected override " + dsWriter.version() + " createDotNetVersion_() {");
                wl("        return new " + _classDef.actualName(g().objectModel()) + ".Version(0);");
                wl("    }");
            }

            wl();

            ObjectModelDef model = g().objectModel();

            if (g().isJava())
                wl("    @Override");

            wl("    protected" + g().target().overrideString() + "int classId_() {");
            wl("        return " + model.fullName() + "." + dsWriter.getClassId() + ";");
            wl("    }");
            wl();

            if (g().isJava()) {
                wl("    @Override");
                wl("    protected" + g().target().overrideString() + "org.objectfabric.ObjectModel objectModel_() {");
                wl("        return " + model.rootPackage().Name + "." + model.Name + ".instance();");
                wl("    }");
            } else {
                wl("    protected override org.objectfabric.ObjectModel ObjectModel_");
                wl("    {");
                wl("        get { return " + model.rootPackage().Name + "." + model.Name + ".Instance; }");
                wl("    }");
            }
        }

        if (_classDef.isTGeneratedFields(g().objectModel()))
            dsWriter.writeVersion();

        wl("}");
    }

    @Override
    void footer() {
        if (g().isCSharp()) {
            untab();

            wl("}");
        }

        super.footer();
    }
}
