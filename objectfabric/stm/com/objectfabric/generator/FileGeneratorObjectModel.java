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

import com.objectfabric.CompileTimeSettings;
import com.objectfabric.misc.List;

class FileGeneratorObjectModel extends FileGenerator {

    public FileGeneratorObjectModel(Generator generator) {
        super(generator, generator.getObjectModel().getRootPackage().getFullName(), generator.getObjectModel().getActualName());
    }

    @Override
    protected String onLine(String line) {
        return FileGeneratorClass.replace(line, g().isCSharp());
    }

    @Override
    protected void header() {
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
            wl("@SuppressWarnings({ \"hiding\", \"unchecked\", \"static-access\" })");

        String public_ = g().getObjectModel().Public ? "public " : "";
        String abstract_ = g().getObjectModel().Abstract ? "abstract " : "";

        wl(public_ + abstract_ + "class " + Name + " " + g().getTarget().extendsString() + " com.objectfabric.ObjectModel {");
        wl();

        byte[] uid = g().getLastObjectModelUID();
        String uidText = "" + (g().isJava() ? uid[0] : uid[0] & 0xff);

        for (int i = 1; i < uid.length; i++)
            uidText += ", " + (g().isJava() ? uid[i] : uid[i] & 0xff);

        wl("    private static " + (g().isCSharp() ? "readonly" : "final") + " byte[] UID = { " + uidText + " };");
        wl();
        wl("    private static volatile " + g().getObjectModel().Name + " _instance;");
        wl();
        wl("    private static " + (g().isCSharp() ? "readonly" : "final") + " Object _lock = new Object();");
        wl();

        // TODO: generate TYPE field

        wl("    protected " + Name + "(com.objectfabric.TObject.Version shared)" + (g().isCSharp() ? "" : " {"));

        if (g().isCSharp()) {
            wl("        : base(shared)");
            wl("    {");
        } else
            wl("        super(shared);");

        wl("    }");
        wl();

        if (!g().getObjectModel().Abstract) {
            wl("    protected " + Name + "()" + (g().isCSharp() ? "" : " {"));

            if (g().isCSharp()) {
                wl("        : this(new Version(null))");
                wl("    {");
            } else
                wl("        this(new Version(null));");

            wl("    }");
            wl();
        }

        if (g().isCSharp()) {
            wl("    public static " + g().getObjectModel().Name + " Instance");
            wl("    {");
            wl("        get");
            wl("        {");
            wl("            if (_instance == null)");
            wl("                lock (_lock)");
            wl("                    if (_instance == null)");
            wl("                        _instance = new " + g().getObjectModel().Name + "();");
            wl();
            wl("            return _instance;");
            wl("        }");
            wl("    }");
        } else {
            wl("    public static " + g().getObjectModel().Name + " getInstance() {");
            wl("        if (_instance == null) {");
            wl("            synchronized (_lock) {");
            wl("                if (_instance == null)");
            wl("                    _instance = new " + g().getObjectModel().Name + "();");
            wl("            }");
            wl("        }");
            wl();
            wl("        return _instance;");
            wl("    }");
        }

        wl();
        wl("    public static byte[] getUID() {");
        wl("        byte[] copy = new byte[UID.length];");
        wl("        com.objectfabric.misc.PlatformAdapter.arraycopy(UID, 0, copy, 0, copy.length);");
        wl("        return copy;");
        wl("    }");
        wl();
        wl("    /**");
        wl("     * Registers this object model so that its classes can be serialized by the");
        wl("     * system.");
        wl("     */");
        wl("    public static void " + (g().isCSharp() ? "R" : "r") + "egister() {");
        wl("        register(" + (g().isCSharp() ? "Instance" : "getInstance()") + ");");
        wl("    }");
        wl();
        wl("    /**");
        wl("     * Registers an object model which can override <code>createInstance</code>");
        wl("     * to let the system use your own derived classes. This is necessary e.g. to");
        wl("     * implement remote methods on transactional objects.");
        wl("     */");
        wl("    public static void " + (g().isCSharp() ? "R" : "r") + "egisterOverridenModel(" + g().getObjectModel().Name + " model) {");

        if (g().isJava())
            wl("        synchronized (_lock) {");
        else
            wl("        lock (_lock) {");

        wl("            if (_instance != null)");
        wl("                throw new RuntimeException(\"Object model has already been registered. This method can only be called at program startup.\");");
        wl();
        wl("            _instance = model;");
        wl("        }");
        wl();
        wl("        register(model);");
        wl("    }");
        wl();

        if (g().isJava())
            wl("    @Override");

        wl("    protected" + g().getTarget().overrideString() + g().getTarget().stringString() + " getObjectFabricVersion() {");
        wl("        return \"" + CompileTimeSettings.OBJECTFABRIC_VERSION + "\";");
        wl("    }");
        wl();
    }

    @Override
    protected void body() {
        List<String> names = g().getObjectModel().getAllFullClassNames();

        wl("    public static final int CLASS_COUNT = " + names.size() + ";");
        wl();

        for (int i = 0; i < g().getObjectModel().getAllClassIds().size(); i++) {
            wl("    public static final int " + g().getObjectModel().getAllClassIds().get(i) + " = " + i + ";");
            wl();
        }

        List<MethodDef> methods = g().getObjectModel().getAllMethods();

        wl("    public static final int METHOD_COUNT = " + methods.size() + ";");
        wl();

        for (int i = 0; i < g().getObjectModel().getAllMethodIds().size(); i++) {
            wl("    public static final int " + g().getObjectModel().getAllMethodIds().get(i) + " = " + (i + names.size()) + ";");
            wl();
        }

        if (g().isJava())
            wl("    @Override");

        String types = g().isCSharp() ? "" : ", com.objectfabric.TType[] genericParameters";

        wl("    protected" + g().getTarget().overrideString() + g().getTarget().classString() + " getClass(int classId" + types + ") {");
        wl("        switch (classId) {");

        for (int i = 0; i < g().getObjectModel().getAllClassIds().size(); i++) {
            wl("            case " + g().getObjectModel().getAllClassIds().get(i) + ":");

            if (g().isJava())
                wl("                return " + names.get(i) + ".class;");
            else
                wl("                return typeof(" + names.get(i) + ");");
        }

        for (int i = 0; i < g().getObjectModel().getAllMethodIds().size(); i++) {
            wl("            case " + g().getObjectModel().getAllMethodIds().get(i) + ":");

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

        String ret = g().isCSharp() ? "object" : "com.objectfabric.TObject.UserTObject";
        wl("    protected" + g().getTarget().overrideString() + ret + " createInstance(com.objectfabric.Transaction trunk, int classId" + types + ") {");
        wl("        switch (classId) {");

        for (int i = 0; i < g().getObjectModel().getAllClasses().size(); i++) {
            StringBuilder args = new StringBuilder();
            List<ValueDef> values = g().getObjectModel().getAllClasses().get(i).getAllValues(g().getObjectModel());

            for (int t = 0; t < values.size(); t++)
                if (values.get(t).isReadOnly())
                    args.append(", " + values.get(t).getType().getDefaultString());

            wl("            case " + g().getObjectModel().getAllClassIds().get(i) + ":");
            wl("                return new " + names.get(i) + "(trunk" + args + ");");
        }

        for (int i = 0; i < g().getObjectModel().getAllMethodIds().size(); i++) {
            wl("            case " + g().getObjectModel().getAllMethodIds().get(i) + ":");
            wl("                return Method" + i + ".INSTANCE;");
        }

        wl("        }");
        wl();

        if (g().isJava())
            wl("        return super.createInstance(trunk, classId, genericParameters);");
        else
            wl("        return base.createInstance(trunk, classId);");

        wl("    }");
        wl();

        if (g().isJava())
            wl("    protected static final class Version extends com.objectfabric.ObjectModel.Version {");
        else
            wl("    new protected class Version : com.objectfabric.ObjectModel.Version {");

        wl();

        if (g().isJava()) {
            wl("        public Version(com.objectfabric.ObjectModel.Version shared) {");
            wl("            super(shared);");
        } else {
            wl("        public Version(com.objectfabric.ObjectModel.Version shared)");
            wl("            : base(shared) {");
        }

        wl("        }");
        wl();

        if (g().isJava())
            wl("        @Override");

        wl("        public" + g().getTarget().overrideString() + "byte[] getUID() {");
        wl("            return " + Name + ".UID;");
        wl("        }");
        wl("    }");

        for (int i = 0; i < methods.size(); i++) {
            String ext;

            if (methods.get(i).lessOr32Fields(g().getObjectModel()))
                ext = "com.objectfabric.TGeneratedFields32";
            else
                ext = "com.objectfabric.TGeneratedFieldsN";

            ext += (g().isJava() ? " implements" : ",") + " com.objectfabric.TObject.UserTObject.Method";

            String name = methods.get(i).getActualName(g().getObjectModel());

            wl();
            wl("    public " + (g().isCSharp() ? "" : "static ") + "class " + name + " " + g().getTarget().extendsString() + " " + ext + " {");
            wl();
            wl("        public static final " + name + " INSTANCE = new " + name + "(com.objectfabric.Site.getLocal().getTrunk());");
            wl();

            if (g().isJava()) {
                wl("        private " + name + "(com.objectfabric.Transaction trunk) {");
                wl("            super(new Version(null, FIELD_COUNT), trunk);");
            } else {
                wl("        private " + name + "(com.objectfabric.Transaction trunk)");
                wl("            : base(new Version(null, FIELD_COUNT), trunk) {");
            }

            wl("        }");
            wl();
            wl("        public String getName() {");
            wl("            return \"" + methods.get(i).Name + "\";");
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
    protected void footer() {
        if (g().isCSharp()) {
            wl("}");
            untab();
        }

        wl("}");
    }
}
