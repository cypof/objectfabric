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

class FileGeneratorObjectModel extends FileGenerator {

    FileGeneratorObjectModel(GeneratorBase generator) {
        super(generator, generator.objectModel().rootPackage().fullName(), generator.objectModel().Name);
    }

    @Override
    String onLine(String line) {
        return FileGeneratorClass.replace(line, g().isCSharp());
    }

    @Override
    void header() {
        copyright();

        wl();

        if (g().isCSharp()) {
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

        if (g().isJava())
            wl("@SuppressWarnings({ \"hiding\", \"unchecked\", \"static-access\", \"rawtypes\" })");

        String public_ = g().objectModel().Public ? "public " : "";
        String final_ = g().isCSharp() ? "sealed " : "final ";

        wl(public_ + final_ + "class " + Name + " " + g().target().extendsString() + " org.objectfabric.ObjectModel {");
        wl();

        byte[] uid = g().objectModelUID();
        String uidText = "" + (g().isJava() ? uid[0] : uid[0] & 0xff);

        for (int i = 1; i < uid.length; i++)
            uidText += ", " + (g().isJava() ? uid[i] : uid[i] & 0xff);

        wl("    private static " + (g().isCSharp() ? "readonly" : "final") + " byte[] UID = { " + uidText + " };");
        wl();
        wl("    // volatile not needed, models have no state");
        wl("    private static " + g().objectModel().Name + " _instance;");
        wl();
        wl("    private static " + (g().isCSharp() ? "readonly" : "final") + " java.lang.Object _lock = new java.lang.Object();");
        wl();
        wl("    protected " + Name + "() {");
        wl("    }");
        wl();

        if (g().isCSharp()) {
            wl("    public static " + g().objectModel().Name + " Instance");
            wl("    {");
            wl("        get");
            wl("        {");
            wl("            if (_instance == null)");
            wl("                lock (_lock)");
            wl("                    if (_instance == null)");
            wl("                        _instance = new " + g().objectModel().Name + "();");
            wl();
            wl("            return _instance;");
            wl("        }");
            wl("    }");
        } else {
            wl("    public static " + g().objectModel().Name + " instance() {");
            wl("        if (_instance == null) {");
            wl("            synchronized (_lock) {");
            wl("                if (_instance == null)");
            wl("                    _instance = new " + g().objectModel().Name + "();");
            wl("            }");
            wl("        }");
            wl();
            wl("        return _instance;");
            wl("    }");
        }

        wl();
        wl("    public static byte[] uid() {");
        wl("        byte[] copy = new byte[UID.length];");
        wl("        arraycopy(UID, copy);");
        wl("        return copy;");
        wl("    }");
        wl();

        if (g().isJava())
            wl("    @Override");

        wl("    protected" + g().target().overrideString() + "byte[] uid_() {");
        wl("        return UID;");
        wl("    }");
        wl();
        wl("    /**");
        wl("     * Registers this object model so that its classes can be serialized by the");
        wl("     * system.");
        wl("     */");
        wl("    public static void " + (g().isCSharp() ? "R" : "r") + "egister() {");
        wl("        register(" + (g().isCSharp() ? "Instance" : "instance()") + ");");
        wl("    }");
        wl();

        if (g().isJava())
            wl("    @Override");

        wl("    protected" + g().target().overrideString() + g().target().stringString() + " objectFabricVersion() {");
        wl("        return \"" + TObject.OBJECT_FABRIC_VERSION + "\";");
        wl("    }");
        wl();
    }

    @Override
    void body() {
        List<String> names = g().objectModel().allFullClassNames();

        wl("    public static final int CLASS_COUNT = " + names.size() + ";");
        wl();

        for (int i = 0; i < g().objectModel().allClassIds().size(); i++) {
            wl("    public static final int " + g().objectModel().allClassIds().get(i) + " = " + i + ";");
            wl();
        }

        List<MethodDef> methods = g().objectModel().allMethods();

        wl("    public static final int METHOD_COUNT = " + methods.size() + ";");
        wl();

        for (int i = 0; i < g().objectModel().allMethodIds().size(); i++) {
            wl("    public static final int " + g().objectModel().allMethodIds().get(i) + " = " + (i + names.size()) + ";");
            wl();
        }

        if (g().isJava())
            wl("    @Override");

        String types = g().isCSharp() ? "" : ", org.objectfabric.TType[] genericParameters";

        wl("    protected" + g().target().overrideString() + g().target().classString() + " getClass(int classId" + types + ") {");
        wl("        switch (classId) {");

        for (int i = 0; i < g().objectModel().allClassIds().size(); i++) {
            wl("            case " + g().objectModel().allClassIds().get(i) + ":");

            if (g().isJava())
                wl("                return " + names.get(i) + ".class;");
            else
                wl("                return typeof(" + names.get(i) + ");");
        }

        for (int i = 0; i < g().objectModel().allMethodIds().size(); i++) {
            wl("            case " + g().objectModel().allMethodIds().get(i) + ":");

            if (g().isJava())
                wl("                return Method" + i + ".class;");
            else
                wl("                return typeof(Method" + i + ");");
        }

        wl("        }");
        wl();

        if (g().isJava())
            wl("        return super.getClass(classId, genericParameters);");
        else
            wl("        return base.getClass(classId);");

        wl("    }");
        wl();

        if (g().isJava())
            wl("    @Override");

        String ret = g().isCSharp() ? "object" : "org.objectfabric.TObject";
        wl("    protected" + g().target().overrideString() + ret + " createInstance(org.objectfabric.Resource resource, int classId" + types + ") {");
        wl("        switch (classId) {");

        for (int i = 0; i < g().objectModel().allClasses().size(); i++) {
            StringBuilder args = new StringBuilder();
            List<ValueDef> values = g().objectModel().allClasses().get(i).allValues(g().objectModel());

            for (int t = 0; t < values.size(); t++)
                if (values.get(t).isReadOnly())
                    args.append(", " + values.get(t).type().defaultString());

            wl("            case " + g().objectModel().allClassIds().get(i) + ":");
            wl("                return new " + names.get(i) + "(resource" + args + ");");
        }

        for (int i = 0; i < g().objectModel().allMethodIds().size(); i++) {
            wl("            case " + g().objectModel().allMethodIds().get(i) + ":");
            wl("                return new Method" + i + "(resource);");
        }

        wl("        }");
        wl();

        if (g().isJava())
            wl("        return super.createInstance(resource, classId, genericParameters);");
        else
            wl("        return base.createInstance(resource, classId);");

        wl("    }");
        wl();

        for (int i = 0; i < methods.size(); i++) {
            String ext;

            if (methods.get(i).lessOr32Fields(g().objectModel()))
                ext = "org.objectfabric.TIndexed.Version32";
            else
                ext = "org.objectfabric.TIndexed.VersionN";

            ext += (g().isJava() ? " implements" : ",") + " org.objectfabric.TObject.UserTObject.Method";

            String name = methods.get(i).actualName(g().objectModel());

            wl();
            wl("    public " + (g().isCSharp() ? "" : "static ") + "class " + name + " " + g().target().extendsString() + " " + ext + " {");
            wl();
            wl("        private static final org.objectfabric.TObject.UserTObject.MethodCache<" + name + "> _cache = new org.objectfabric.TObject.UserTObject.MethodCache<" + name + ">() {");
            wl();

            if (g().isJava())
                wl("            @Override");

            wl("            protected " + name + " create(java.lang.Object param) {");
            wl("                return new " + name + "((org.objectfabric.View) param);");
            wl("            }");
            wl("        };");
            wl();

            if (g().isJava()) {
                wl("        private " + name + "(org.objectfabric.Resource resource) {");
                wl("            super(resource, new Version(null, FIELD_COUNT));");
            } else {
                wl("        internal " + name + "(org.objectfabric.Resource resource)");
                wl("            : base(resource, new Version(null, FIELD_COUNT)) {");
            }

            wl("        }");
            wl();
            wl("        public java.lang.String name() {");
            wl("            return \"" + methods.get(i).Name + "\";");
            wl("        }");
            wl();
            wl("        public static " + name + " getOrCreateInstance(org.objectfabric.Resource resource) {");
            wl("            return _cache.getOrCreate(resource);");
            wl("        }");
            wl();

            if (g().isJava())
                wl("        @Override");

            wl("        public void release() {");
            wl("            _cache.recycle(this);");
            wl("        }");

            tab();

            FileGeneratorValueSet dsWriter = new FileGeneratorValueSet(g(), methods.get(i), this);
            dsWriter.writeType();
            dsWriter.writeFieldsConstantsAndCount();
            dsWriter.writeVersion();

            untab();

            wl("    }");
        }
    }

    @Override
    void footer() {
        if (g().isCSharp()) {
            wl("}");
            untab();
        }

        wl("}");
    }
}
