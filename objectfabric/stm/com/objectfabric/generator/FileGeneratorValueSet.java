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

import com.objectfabric.ImmutableClass;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.Utils;

class FileGeneratorValueSet {

    protected final Generator _gen;

    protected final ValueSetDef _valueSet;

    protected final FileGenerator _file;

    protected FileGeneratorValueSet(Generator generator, ValueSetDef valueSet, FileGenerator file) {
        _gen = generator;
        _valueSet = valueSet;
        _file = file;
    }

    private String getVersionName() {
        if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
            return "TIndexed32Version";

        return "TIndexedNVersion";
    }

    protected void writeFieldGet(String cast, ValueDef value) {
        ImmutableClass immutable = value.getType().getImmutable();
        immutable = immutable != null ? immutable : value.getType().getCustomUnderlyingImmutable();
        String default_ = immutable != null ? immutable.getDefaultString() : "null";
        boolean tobj = value.getType().getOtherClass() == PlatformClass.getObjectClass() || value.getType().isTObject();
        String getField = (tobj ? cast + "getUserTObject_objectfabric(" : "") + "v._" + value.Name + (tobj ? ")" : "");

        wl("        com.objectfabric.Transaction outer = com.objectfabric.Transaction.getCurrent();");
        wl("        com.objectfabric.Transaction inner = startRead_objectfabric(outer);");
        wl("        Version v = (Version) get" + getVersionName() + "_objectfabric(inner, " + value.getNameAsConstant() + "_INDEX);");
        wl("        " + value.getType().getFullName(_gen.getTarget(), true) + " value = v != null ? " + getField + " : " + default_ + ";");
        wl("        endRead_objectfabric(outer, inner);");
        wl("        return value;");
    }

    protected void writeFieldGetReadOnly(String cast, ValueDef value) {
        wl("        Version v = (Version) getSharedVersion_objectfabric();");
        boolean tobj = value.getType().getOtherClass() == PlatformClass.getObjectClass() || value.getType().isTObject();
        wl("        return " + (tobj ? cast + "getUserTObject_objectfabric(" : "") + "v._" + value.Name + (tobj ? ")" : "") + ";");
    }

    protected void writeFieldSet(ValueDef value) {
        wl("        com.objectfabric.Transaction outer = com.objectfabric.Transaction.getCurrent();");
        wl("        com.objectfabric.Transaction inner = startWrite_objectfabric(outer);");
        wl("        Version v = (Version) getOrCreateVersion_objectfabric(inner);");
        wl("        v._" + value.Name + " = value;");
        wl("        v.setBit(" + value.getNameAsConstant() + "_INDEX);");
        wl("        endWrite_objectfabric(outer, inner);");
    }

    protected void writeField(int index) {
        if (Debug.ENABLED)
            Debug.assertion(!(_valueSet instanceof MethodDef));

        wl();

        ValueDef value = _valueSet.getValue(index);
        String readVisibility = (value.getPublic().equals(FieldDef.TRUE) || value.getPublic().equals(FieldDef.READ)) ? "public" : "protected";
        String writeVisibility = value.getPublic().equals(FieldDef.TRUE) ? "public" : "protected";
        String type = value.getType().getFullName(_gen.getTarget(), true);
        String name = Utils.getWithFirstLetterUp(value.Name);
        String cast = value.getType().isTObject() ? "(" + type + ") " : "";

        if (_gen.isCSharp()) {
            if (value.Comment != null && value.Comment.length() > 0) {
                wl("    /// <summary>");
                wl("    /// " + value.Comment);
                wl("    /// </summary>");
            }

            wl("    public " + type + " " + name);
            wl("    {");
            wl("        get");
            wl("        {");
            tab();

            if (value.isReadOnly())
                writeFieldGetReadOnly(cast, value);
            else
                writeFieldGet(cast, value);

            untab();
            wl("        }");

            if (!value.isReadOnly()) {
                wl("        set");
                wl("        {");

                tab();
                writeFieldSet(value);
                untab();

                wl("        }");
            }

            wl("    }");
        } else {
            // Getter
            {
                if (value.Comment != null && value.Comment.length() > 0)
                    wl("    /** " + value.Comment + " */");

                wl("    " + readVisibility + " final " + type + " get" + name + "() {");

                if (value.isReadOnly())
                    writeFieldGetReadOnly(cast, value);
                else
                    writeFieldGet(cast, value);
                wl("    }");
            }

            // TODO: generate async getter if field is transient
            // + if (_gen.getFlags().contains(Flag.GenerateSynchronousMethods))

            if (!value.isReadOnly()) { // Setter
                wl();

                if (value.Comment != null && value.Comment.length() > 0)
                    wl("    /** " + value.Comment + " */");

                wl("    " + writeVisibility + " final void set" + name + "(" + type + " value) {");

                writeFieldSet(value);

                wl("    }");
            }
        }
    }

    public void writeType() {
        wl();

        ObjectModelDef model = _gen.getObjectModel();
        String m = model.getRootPackage().Name + "." + model.Name + (_gen.isCSharp() ? ".Instance" : ".getInstance()");
        String c = model.getFullName() + "." + getClassId();

        if (_gen.isCSharp()) {
            wl("    #pragma warning disable 109");
            wl("    new public static readonly com.objectfabric.TType TYPE = getTType_objectfabric(" + m + ", " + c + ");");
            wl("    #pragma warning restore 109");
        } else
            wl("    public static final com.objectfabric.TType TYPE = new com.objectfabric.TType(" + m + ", " + c + ");");
    }

    public void writeFields() {
        for (int i = 0; i < _valueSet.getValues().size(); i++)
            writeField(i);
    }

    public void writeFieldsConstantsAndCount() {
        int startIndex = 0;

        GeneratedClassDef parent = _valueSet.getParentGeneratedClass(_gen.getObjectModel());

        if (parent != null)
            startIndex = parent.getAllValues(_gen.getObjectModel()).size();

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            wl();

            String constant = _valueSet.getValue(i).getNameAsConstant();
            String visibility = _valueSet.getValue(i).getPublic().equals(FieldDef.TRUE) ? "public" : "protected";

            wl("    " + visibility + " static final int " + constant + "_INDEX = " + startIndex++ + ";");
            wl();

            String name = "\"" + _valueSet.getValue(i).Name + "\"";

            wl("    " + visibility + " static final " + _gen.getTarget().stringString() + " " + constant + "_NAME = " + name + ";");
            wl();

            String t = _valueSet.getValue(i).getType().getTTypeString(_gen.getTarget());
            wl("    " + visibility + " static " + (_gen.isCSharp() ? "readonly" : "final") + " com.objectfabric.TType " + constant + "_TYPE = " + t + ";");
        }

        wl();
        wl("    public static final int FIELD_COUNT = " + _valueSet.getAllValues(_gen.getObjectModel()).size() + ";");
        wl();

        if (_gen.isJava())
            wl("    @Override");

        wl("    public" + _gen.getTarget().overrideString() + "int getFieldCount() {");
        wl("        return FIELD_COUNT;");
        wl("    }");

        wl();

        String g = _gen.isCSharp() ? "G" : "g";

        if (_gen.isJava())
            wl("    @Override");

        wl("    public" + _gen.getTarget().overrideString() + _gen.getTarget().stringString() + " getFieldName(int index) {");
        wl("        return " + g + "etFieldNameStatic(index);");
        wl("    }");
        wl();

        if (_gen.isJava())
            wl("    @SuppressWarnings(\"static-access\")");

        wl("    public static " + _gen.getTarget().stringString() + " " + g + "etFieldNameStatic(int index) {");
        wl("        switch (index) {");

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            String constant = _valueSet.getValue(i).getNameAsConstant();

            wl("            case " + constant + "_INDEX:");
            wl("                return " + constant + "_NAME;");
        }

        wl("            default:");

        if (_valueSet.getParent() != null)
            wl("                return " + _valueSet.getParent().getFullName(_gen.getTarget()) + "." + g + "etFieldNameStatic(index);");
        else
            wl("                throw new IllegalArgumentException();");

        wl("        }");
        wl("    }");
        wl();

        if (_gen.isJava())
            wl("    @Override");

        wl("    public" + _gen.getTarget().overrideString() + " com.objectfabric.TType " + g + "etFieldType(int index) {");
        wl("        return " + g + "etFieldTypeStatic(index);");
        wl("    }");
        wl();

        if (_gen.isJava())
            wl("    @SuppressWarnings(\"static-access\")");

        wl("    public static com.objectfabric.TType " + g + "etFieldTypeStatic(int index) {");
        wl("        switch (index) {");

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            String constant = _valueSet.getValue(i).getNameAsConstant();

            wl("            case " + _valueSet.getValue(i).getNameAsConstant() + "_INDEX:");
            wl("                return " + constant + "_TYPE;");
        }

        wl("            default:");

        if (_valueSet.getParent() != null)
            wl("                return " + _valueSet.getParent().getFullName(_gen.getTarget()) + "." + g + "etFieldTypeStatic(index);");
        else
            wl("                throw new IllegalArgumentException();");

        wl("        }");
        wl("    }");
    }

    public void writeVersion() {
        String visibility = _valueSet instanceof MethodDef ? "public " : "protected ";
        String static_ = _gen.isCSharp() ? "" : "static ";
        String new_ = _gen.isCSharp() ? "new " : "";

        wl();

        String parent;

        if (_valueSet.getParent() != null)
            parent = _valueSet.getParent().getFullName(_gen.getTarget()) + ".Version";
        else {
            if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                parent = "com.objectfabric.TGeneratedFields32.Version";
            else
                parent = "com.objectfabric.TGeneratedFieldsN.Version";
        }

        wl("    " + new_ + visibility + static_ + "class Version " + _gen.getTarget().extendsString() + " " + parent + " {");

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            ValueDef value = _valueSet.getValue(i);
            wl();

            if (value.getType().isTObject()) {
                String type = _gen.isJava() ? "com.objectfabric.TObject" : "object";
                wl("        public " + type + " _" + value.Name + ";");
            } else
                wl("        public " + value.getType().getFullName(_gen.getTarget()) + " _" + value.Name + ";");
        }

        for (TypeDef type : _valueSet.getEnums()) {
            wl();
            String c = type.getFullName(_gen.getTarget());
            String name = ObjectModelDef.formatAsConstant(c) + TypeDef.ENUM_VALUES_ARRAY;
            wl("        private static final " + c + "[] " + name + " = " + c + ".values();");
        }

        boolean hasReadOnlys = false, hasTransients = false;

        if (!(_valueSet instanceof MethodDef)) {
            List<ValueDef> all = _valueSet.getAllValues(_gen.getObjectModel());

            for (int i = 0; i < all.size(); i++)
                if (all.get(i).isReadOnly())
                    hasReadOnlys = true;

            for (int i = 0; i < all.size(); i++)
                if (all.get(i).isTransient())
                    hasTransients = true;

            String final_ = _gen.isCSharp() ? "readonly" : "final";

            if (hasReadOnlys) {
                wl();

                if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                    wl("        private static " + final_ + " int _readOnlys;");
                else
                    wl("        private static " + final_ + " com.objectfabric.misc.Bits.Entry[] _readOnlys;");
            }

            if (hasTransients) {
                wl();

                if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                    wl("        private static " + final_ + " int _transients;");
                else
                    wl("        private static " + final_ + " com.objectfabric.misc.Bits.Entry[] _transients;");
            }

            wl();
            wl("        static" + (_gen.isCSharp() ? " Version()" : "") + " {");

            if (hasReadOnlys) {
                if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                    wl("            int readOnlys = 0;");
                else
                    wl("            com.objectfabric.misc.Bits.Entry[] readOnlys = new com.objectfabric.misc.Bits.Entry[com.objectfabric.misc.Bits.SPARSE_BITSET_DEFAULT_CAPACITY];");

                for (int i = 0; i < all.size(); i++)
                    if (all.get(i).isReadOnly())
                        wl("            readOnlys = setBit(readOnlys, " + all.get(i).getNameAsConstant() + "_INDEX);");

                wl("            _readOnlys = readOnlys;");
            }

            if (hasTransients) {
                if (hasReadOnlys)
                    wl();

                if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                    wl("            int transients = 0;");
                else
                    wl("            com.objectfabric.misc.Bits.Entry[] transients = new com.objectfabric.misc.Bits.Entry[com.objectfabric.misc.Bits.SPARSE_BITSET_DEFAULT_CAPACITY];");

                for (int i = 0; i < all.size(); i++)
                    if (all.get(i).isTransient())
                        wl("            transients = setBit(transients, " + all.get(i).getNameAsConstant() + "_INDEX);");

                wl("            _transients = transients;");
            }

            wl("        }");
        }

        wl();
        wl("        public Version(" + parent + " shared, int length)" + (_gen.isCSharp() ? "" : " {"));

        if (_gen.isCSharp()) {
            wl("            : base(shared, length)");
            wl("        {");
        } else
            wl("            super(shared, length);");

        wl("        }");

        if (!(_valueSet instanceof MethodDef)) {
            wl();

            if (_gen.isJava())
                wl("        @Override");

            wl("        public" + _gen.getTarget().overrideString() + _gen.getTarget().objectString() + " getAsObject(int index) {");
            wl("            switch (index) {");

            for (int i = 0; i < _valueSet.getValues().size(); i++) {
                ValueDef value = _valueSet.getValue(i);
                wl("                case " + value.getNameAsConstant() + "_INDEX:");

                if (value.getType().isTObject())
                    wl("                    return getUserTObject_objectfabric(_" + value.Name + ");");
                else
                    wl("                    return _" + value.Name + ";");
            }

            wl("                default:");
            wl("                    return " + (_gen.isCSharp() ? "base" : "super") + ".getAsObject(index);");
            wl("            }");
            wl("        }");
            wl();

            if (_gen.isJava())
                wl("        @Override");

            wl("        public" + _gen.getTarget().overrideString() + "void setAsObject(int index, " + _gen.getTarget().objectString() + " value) {");
            wl("            switch (index) {");

            for (int i = 0; i < _valueSet.getValues().size(); i++) {
                ValueDef value = _valueSet.getValue(i);
                wl("                case " + value.getNameAsConstant() + "_INDEX:");

                String rightPart = value.getType().getImmutable() != null ? value.getType().getImmutable().getDefaultString() : "null";
                ImmutableClass immutable = value.getType().getImmutable();

                if (_gen.isJava() && immutable != null && immutable.isPrimitive() && !immutable.isBoxed())
                    rightPart = "(" + value.getType().getCast(_gen) + "value)." + immutable.getJava() + "Value()";
                else
                    rightPart = value.getType().getCast(_gen) + "value";

                wl("                    _" + value.Name + " = " + rightPart + ";");
                wl("                    break;");
            }

            wl("                default:");
            wl("                    " + (_gen.isCSharp() ? "base" : "super") + ".setAsObject(index, value);");
            wl("                    break;");
            wl("            }");
            wl("        }");
        }

        wl();

        String version = "com.objectfabric.TObject.Version";

        if (_gen.isCSharp()) {
            if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                version = "ObjectFabric.TGeneratedFields32.Version";
            else
                version = "ObjectFabric.TGeneratedFieldsN.Version";
        }

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.getTarget().overrideString() + (_gen.isCSharp() ? "void" : version) + " merge(" + version + " target, " + version + " next, int flags) {");

        if (_valueSet.getValues().size() > 0)
            wl("            " + _valueSet.getActualName(_gen.getObjectModel()) + ".Version source = (" + _valueSet.getActualName(_gen.getObjectModel()) + ".Version) next;");

        wl("            " + _valueSet.getActualName(_gen.getObjectModel()) + ".Version merged = (" + _valueSet.getActualName(_gen.getObjectModel()) + ".Version) " + _gen.getTarget().superString() + ".merge(target, next, flags);");

        if (_valueSet.getValues().size() > 0) {
            wl();
            wl("            if (source.hasBits()) {");
        }

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            if (i != 0)
                wl();

            ValueDef value = _valueSet.getValue(i);
            wl("                if (source.getBit(" + value.getNameAsConstant() + "_INDEX))");

            if (value.getType().getOtherClass() == PlatformClass.getObjectClass())
                wl("                    merged._" + value.Name + " = mergeObject(merged._" + value.Name + ", source._" + value.Name + ");");
            else if (value.getType().isTObject())
                wl("                    merged._" + value.Name + " = mergeTObject(merged._" + value.Name + ", source._" + value.Name + ");");
            else
                wl("                    merged._" + value.Name + " = source._" + value.Name + ";");
        }

        if (_valueSet.getValues().size() > 0) {
            wl("            }");
            wl();
        }

        wl("            return merged;");
        wl("        }");

        if (hasReadOnlys) {
            wl();

            if (_gen.isJava())
                wl("        @Override");

            if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                wl("        public" + _gen.getTarget().overrideString() + "int getReadOnlys() {");
            else
                wl("        public" + _gen.getTarget().overrideString() + "com.objectfabric.misc.Bits.Entry[] getReadOnlys() {");

            wl("            return _readOnlys;");
            wl("        }");
        }

        if (hasTransients) {
            wl();

            if (_gen.isJava())
                wl("        @Override");

            if (_valueSet.lessOr32Fields(_gen.getObjectModel()))
                wl("        public" + _gen.getTarget().overrideString() + "int getTransients() {");
            else
                wl("        public" + _gen.getTarget().overrideString() + "com.objectfabric.misc.Bits.Entry[] getTransients() {");

            wl("            return _transients;");
            wl("        }");
        }

        writeSerialization();

        if (_valueSet instanceof MethodDef) {
            wl();

            if (_gen.isJava())
                wl("        @Override");

            wl("        public" + _gen.getTarget().overrideString() + "com.objectfabric.TObject.Version createRead() {");
            wl("            return null;");
            wl("        }");
        }

        wl();

        if (_gen.isJava()) {
            wl("        @Override");
            wl("        public com.objectfabric.TObject.Version createVersion() {");
            wl("            return new " + _valueSet.getActualName(_gen.getObjectModel()) + ".Version(this, FIELD_COUNT);");
            wl("        }");
        } else {
            wl("        public override " + version + " createDotNetVersion() {");
            wl("            return new " + _valueSet.getActualName(_gen.getObjectModel()) + ".Version(this, FIELD_COUNT);");
            wl("        }");
        }

        wl();

        ObjectModelDef model = _gen.getObjectModel();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.getTarget().overrideString() + "int getClassId() {");
        wl("            return " + model.getFullName() + "." + getClassId() + ";");
        wl("        }");
        wl();

        if (_gen.isJava()) {
            wl("        @SuppressWarnings(\"static-access\")");
            wl("        @Override");
            wl("        public" + _gen.getTarget().overrideString() + "com.objectfabric.ObjectModel getObjectModel() {");
            wl("            return " + model.getRootPackage().Name + "." + model.Name + ".getInstance();");
            wl("        }");
        } else {
            wl("        public override com.objectfabric.ObjectModel ObjectModel");
            wl("        {");
            wl("            get { return " + model.getRootPackage().Name + "." + model.Name + ".Instance; }");
            wl("        }");
        }

        wl("    }");
    }

    private String getClassId() {
        String id;

        if (_valueSet instanceof GeneratedClassDef) {
            int index = _gen.getObjectModel().getAllClasses().indexOf(_valueSet);
            id = _gen.getObjectModel().getAllClassIds().get(index);
        } else {
            int index = _gen.getObjectModel().getAllMethods().indexOf(_valueSet);
            id = _gen.getObjectModel().getAllMethodIds().get(index);
        }

        return id;
    }

    private void writeSerialization() {
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.getTarget().overrideString() + "void writeWrite(" + (_gen.isCSharp() ? "object" : "com.objectfabric.Writer") + " writer, int index) {");

        String writerObj = _gen.isJava() ? "writer." : "";
        String writerArg = _gen.isJava() ? "" : "writer";
        String writerArgC = _gen.isJava() ? "" : "writer, ";

        wl("            if (" + writerObj + "interrupted(" + writerArg + "))");
        wl("                " + writerObj + "resume(" + writerArg + ");");
        wl();
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            ValueDef value = _valueSet.getValue(i);
            boolean fixedLength = value.getType().fixedLength();

            wl("                case " + value.getNameAsConstant() + "_INDEX: {");

            if (fixedLength) {
                String canWrite = null;

                ImmutableClass immutable = value.getType().getImmutable();

                if (immutable == null)
                    if (value.getType().getCustomUnderlyingImmutable() != null)
                        immutable = value.getType().getCustomUnderlyingImmutable();

                if (immutable != null)
                    canWrite = "canWrite" + immutable + "(" + writerArg + ")";

                if (value.getType().isJavaEnum())
                    canWrite = "canWriteInteger(" + writerArg + ")";

                wl("                    if (!" + writerObj + canWrite + ") {");
                wl("                        " + writerObj + "interrupt(" + writerArgC + "null);");
                wl("                        return;");
                wl("                    }");
                wl();
            }

            String write;

            if (value.getType().getImmutable() != null)
                write = "write" + value.getType().getImmutable() + "(" + writerArgC + "_" + value.Name + ")";
            else if (value.getType().getCustom() != null) {
                ImmutableClass immutable = value.getType().getCustomUnderlyingImmutable();
                String cast = "(" + (_gen.isJava() ? immutable.getJava() : immutable.getCSharp()) + ")";
                write = "write" + immutable + "(" + writerArgC + cast + " _" + value.Name + ")";
            } else if (value.getType().isTObject())
                write = "writeTObject(" + writerArgC + "_" + value.Name + ")";
            else if (value.getType().isJavaEnum())
                write = "writeInteger(" + writerArgC + "_" + value.Name + ".ordinal())";
            else
                write = "writeObject(" + writerArgC + "_" + value.Name + ")";

            wl("                    " + writerObj + write + ";");

            if (!fixedLength) {
                wl();
                wl("                    if (" + writerObj + "interrupted(" + writerArg + ")) {");
                wl("                        " + writerObj + "interrupt(" + writerArgC + "null);");
                wl("                        return;");
                wl("                    }");
            }

            wl("                    break;");
            wl("                }");
        }

        wl("                default: {");
        wl("                    " + (_gen.isCSharp() ? "base" : "super") + ".writeWrite(writer, index);");
        wl();
        wl("                    if (" + writerObj + "interrupted(" + writerArg + ")) {");
        wl("                        " + writerObj + "interrupt(" + writerArgC + "null);");
        wl("                        return;");
        wl("                    }");
        wl("                    break;");
        wl("                }");
        wl("            }");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.getTarget().overrideString() + "void readWrite(" + (_gen.isCSharp() ? "object" : "com.objectfabric.Reader") + " reader, int index) {");

        String readerObj = _gen.isJava() ? "reader." : "";
        String readerArg = _gen.isJava() ? "" : "reader";
        String readerArgC = _gen.isJava() ? "" : "reader, ";

        wl("            if (" + readerObj + "interrupted(" + readerArg + "))");
        wl("                " + readerObj + "resume(" + readerArg + ");");
        wl();
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.getValues().size(); i++) {
            ValueDef value = _valueSet.getValue(i);
            boolean fixedLength = value.getType().fixedLength();

            wl("                case " + value.getNameAsConstant() + "_INDEX: {");

            if (fixedLength) {
                String canRead = null;

                ImmutableClass immutable = value.getType().getImmutable();

                if (immutable == null)
                    if (value.getType().getCustomUnderlyingImmutable() != null)
                        immutable = value.getType().getCustomUnderlyingImmutable();

                if (immutable != null)
                    canRead = "canRead" + immutable + "(" + readerArg + ")";

                if (value.getType().isJavaEnum())
                    canRead = "canReadInteger()";

                wl("                    if (!" + readerObj + canRead + ") {");
                wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
                wl("                        return;");
                wl("                    }");
                wl();
            }

            String shared = "";

            if (value.isReadOnly()) {
                wl("                    " + _valueSet.getActualName(_gen.getObjectModel()) + ".Version shared = (" + _valueSet.getActualName(_gen.getObjectModel()) + ".Version) getUnion();");
                shared = "shared.";
            }

            String left = shared + "_" + value.Name;
            String right;

            if (value.getType().getImmutable() != null)
                right = readerObj + "read" + value.getType().getImmutable() + "(" + readerArg + ")";
            else if (value.getType().getCustom() != null) {
                ImmutableClass immutable = value.getType().getCustomUnderlyingImmutable();
                String cast = "(" + value.getType().getCustom() + ") ";
                right = cast + readerObj + "read" + immutable + "(" + readerArg + ")";
            } else if (value.getType().isTObject())
                right = readerObj + "readTObject(" + readerArg + ")";
            else if (value.getType().isJavaEnum())
                right = ObjectModelDef.formatAsConstant(value.getType().getFullName(Target.JAVA)) + TypeDef.ENUM_VALUES_ARRAY + "[" + readerObj + "readInteger(" + readerArg + ")]";
            else
                right = readerObj + "readObject(" + readerArg + ")";

            if (value.isReadOnly() && value.getType().isTObject())
                wl("                    " + left + " = getSharedVersion_objectfabric(" + right + ");");
            else
                wl("                    " + left + " = " + right + ";");

            if (!fixedLength) {
                wl();
                wl("                    if (" + readerObj + "interrupted(" + readerArg + ")) {");
                wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
                wl("                        return;");
                wl("                    }");
            }

            if (value.isReadOnly()) {
                wl();
                wl("                    shared.setBit(" + value.getNameAsConstant() + "_INDEX);");
                wl("                    unsetBit(" + value.getNameAsConstant() + "_INDEX);");
            }

            wl("                    break;");
            wl("                }");
        }

        wl("                default: {");
        wl("                    " + (_gen.isCSharp() ? "base" : "super") + ".readWrite(reader, index);");
        wl();
        wl("                    if (" + readerObj + "interrupted(" + readerArg + ")) {");
        wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
        wl("                        return;");
        wl("                    }");
        wl("                    break;");
        wl("                }");
        wl("            }");
        wl("        }");
    }

    protected void wl(String line) {
        _file.wl(line);
    }

    protected void wl() {
        _file.wl();
    }

    protected void tab() {
        _file.tab();
    }

    protected void untab() {
        _file.untab();
    }
}
