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

class FileGeneratorValueSet {

    private final GeneratorBase _gen;

    private final ValueSetDef _valueSet;

    private final FileGenerator _file;

    FileGeneratorValueSet(GeneratorBase generator, ValueSetDef valueSet, FileGenerator file) {
        _gen = generator;
        _valueSet = valueSet;
        _file = file;
    }

    private String getVersionName() {
        if (_valueSet.lessOr32Fields(_gen.objectModel()))
            return "Version32";

        return "VersionN";
    }

    private void writeFieldGet(ValueDef value) {
        Immutable immutable = value.type().immutable();
        immutable = immutable != null ? immutable : value.type().customUnderlyingImmutable();
        String default_ = immutable != null ? immutable.defaultString() : "null";

        wl("        org.objectfabric.TObject.Transaction outer = current_();");
        wl("        org.objectfabric.TObject.Transaction inner = startRead_(outer);");
        wl("        Version v = (Version) get" + getVersionName() + "_(inner, " + value.nameAsConstant() + "_INDEX);");
        wl("        " + value.type().fullName(_gen.target(), true) + " value = v != null ? v._" + value.Name + " : " + default_ + ";");
        wl("        endRead_(outer, inner);");
        wl("        return value;");
    }

    private void writeFieldGetReadOnly(ValueDef value) {
        wl("        Version v = (Version) shared_();");
        wl("        return v._" + value.Name + ";");
    }

    private void writeFieldSet(ValueDef value) {
        if (value.type().isTObject()) {
            wl("        if (value.resource() != resource())");
            wl("            wrongResource_();");
            wl();
        } else if (value.type().canBeTObject()) {
            wl("        if (value instanceof org.objectfabric.TObject && ((org.objectfabric.TObject) value).resource() != resource())");
            wl("            wrongResource_();");
            wl();
        }

        wl("        org.objectfabric.TObject.Transaction outer = current_();");
        wl("        org.objectfabric.TObject.Transaction inner = startWrite_(outer);");
        wl("        Version v = (Version) getOrCreateVersion_(inner);");

        if (value.type().canBeTObject())
            wl("        v._" + value.Name + " = value;");
        else
            wl("        v._" + value.Name + " = value;");

        wl("        v.setBit(" + value.nameAsConstant() + "_INDEX);");
        wl("        endWrite_(outer, inner);");
    }

    private void writeField(int index) {
        if (Debug.ENABLED)
            Debug.assertion(!(_valueSet instanceof MethodDef));

        wl();

        ValueDef value = _valueSet.value(index);
        String readVisibility = (value.publicVisibility().equals(FieldDef.TRUE) || value.publicVisibility().equals(FieldDef.READ)) ? "public" : "protected";
        String writeVisibility = value.publicVisibility().equals(FieldDef.TRUE) ? "public" : "protected";
        String type = value.type().fullName(_gen.target(), true);
        String name = Utils.getWithFirstLetterUp(value.Name);

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
                writeFieldGetReadOnly(value);
            else
                writeFieldGet(value);

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

                if (_gen.addGetAndSet())
                    wl("    " + readVisibility + " final " + type + " get" + name + "() {");
                else
                    wl("    " + readVisibility + " final " + type + " " + value.Name + "() {");

                if (value.isReadOnly())
                    writeFieldGetReadOnly(value);
                else
                    writeFieldGet(value);
                wl("    }");
            }

            // TODO: generate async getter if field is transient
            // + if (_gen.getFlags().contains(Flag.GenerateSynchronousMethods))

            if (!value.isReadOnly()) { // Setter
                wl();

                if (value.Comment != null && value.Comment.length() > 0)
                    wl("    /** " + value.Comment + " */");

                if (_gen.addGetAndSet())
                    wl("    " + writeVisibility + " final void set" + name + "(" + type + " value) {");
                else
                    wl("    " + writeVisibility + " final void " + value.Name + "(" + type + " value) {");

                writeFieldSet(value);

                wl("    }");
            }
        }
    }

    final void writeType() {
        wl();

        ObjectModelDef model = _gen.objectModel();
        String m = model.rootPackage().Name + "." + model.Name + (_gen.isCSharp() ? ".Instance" : ".instance()");
        String c = model.fullName() + "." + getClassId();

        if (_gen.isCSharp()) {
            wl("    #pragma warning disable 109");
            wl("    new public static readonly org.objectfabric.TType TYPE = getTType_(" + m + ", " + c + ");");
            wl("    #pragma warning restore 109");
        } else
            wl("    public static final org.objectfabric.TType TYPE = new org.objectfabric.TType(" + m + ", " + c + ");");
    }

    final void writeFields() {
        for (int i = 0; i < _valueSet.values().size(); i++)
            writeField(i);
    }

    final void writeFieldsConstantsAndCount() {
        int startIndex = 0;

        ClassDef parent = _valueSet.parentGeneratedClass(_gen.objectModel());

        if (parent != null)
            startIndex = parent.allValues(_gen.objectModel()).size();

        for (int i = 0; i < _valueSet.values().size(); i++) {
            wl();

            String constant = _valueSet.value(i).nameAsConstant();
            String visibility = _valueSet.value(i).publicVisibility().equals(FieldDef.TRUE) ? "public" : "protected";

            wl("    " + visibility + " static final int " + constant + "_INDEX = " + startIndex++ + ";");
            wl();

            String name = "\"" + _valueSet.value(i).Name + "\"";

            wl("    " + visibility + " static final " + _gen.target().stringString() + " " + constant + "_NAME = " + name + ";");
            wl();

            String t = _valueSet.value(i).type().getTTypeString(_gen.target());
            wl("    " + visibility + " static " + (_gen.isCSharp() ? "readonly" : "final") + " org.objectfabric.TType " + constant + "_TYPE = " + t + ";");
        }

        wl();
        wl("    public static final int FIELD_COUNT = " + _valueSet.allValues(_gen.objectModel()).size() + ";");
        wl();

        String s = _gen.isCSharp() ? "GetField" : "field";

        wl("    public static " + _gen.target().stringString() + " " + s + "Name(int index) {");
        wl("        switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            String constant = _valueSet.value(i).nameAsConstant();

            wl("            case " + constant + "_INDEX:");
            wl("                return " + constant + "_NAME;");
        }

        wl("            default:");

        if (_valueSet.parent() != null)
            wl("                return " + _valueSet.parent().fullName(_gen.target()) + "." + s + "Name(index);");
        else
            wl("                throw new IllegalArgumentException();");

        wl("        }");
        wl("    }");
        wl();
        wl("    public static org.objectfabric.TType " + s + "Type(int index) {");
        wl("        switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            String constant = _valueSet.value(i).nameAsConstant();

            wl("            case " + _valueSet.value(i).nameAsConstant() + "_INDEX:");
            wl("                return " + constant + "_TYPE;");
        }

        wl("            default:");

        if (_valueSet.parent() != null)
            wl("                return " + _valueSet.parent().fullName(_gen.target()) + "." + s + "Type(index);");
        else
            wl("                throw new IllegalArgumentException();");

        wl("        }");
        wl("    }");
    }

    final void writeVersion() {
        String visibility = _valueSet instanceof MethodDef ? "public " : "protected ";
        String static_ = _gen.isCSharp() ? "" : "static ";
        String new_ = _gen.isCSharp() ? "new " : "";

        wl();

        String parent;

        if (_valueSet.parent() != null)
            parent = _valueSet.parent().fullName(_gen.target()) + ".Version";
        else {
            if (_valueSet.lessOr32Fields(_gen.objectModel()))
                parent = "org.objectfabric.TIndexed.Version32";
            else
                parent = "org.objectfabric.TIndexed.VersionN";
        }

        wl("    " + new_ + visibility + static_ + "class Version " + _gen.target().extendsString() + " " + parent + " {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            ValueDef value = _valueSet.value(i);
            wl();
            wl("        public " + value.type().fullName(_gen.target()) + " _" + value.Name + ";");
        }

        for (TypeDef type : _valueSet.enums()) {
            wl();
            String c = type.fullName(_gen.target());
            String name = ObjectModelDef.formatAsConstant(c) + TypeDef.ENUM_VALUES_ARRAY;
            wl("        private static final " + c + "[] " + name + " = " + c + ".values();");
        }

        boolean hasReadOnlys = false;

        if (!(_valueSet instanceof MethodDef)) {
            List<ValueDef> all = _valueSet.allValues(_gen.objectModel());

            for (int i = 0; i < all.size(); i++)
                if (all.get(i).isReadOnly())
                    hasReadOnlys = true;

            String final_ = _gen.isCSharp() ? "readonly" : "final";

            if (hasReadOnlys) {
                wl();

                if (_valueSet.lessOr32Fields(_gen.objectModel()))
                    wl("        private static " + final_ + " int _readOnlys;");
                else
                    wl("        private static " + final_ + " org.objectfabric.misc.Bits.Entry[] _readOnlys;");
            }

            wl();
            wl("        static" + (_gen.isCSharp() ? " Version()" : "") + " {");

            if (hasReadOnlys) {
                if (_valueSet.lessOr32Fields(_gen.objectModel()))
                    wl("            int readOnlys = 0;");
                else
                    wl("            org.objectfabric.misc.Bits.Entry[] readOnlys = new org.objectfabric.misc.Bits.Entry[org.objectfabric.misc.Bits.SPARSE_BITSET_DEFAULT_CAPACITY];");

                for (int i = 0; i < all.size(); i++)
                    if (all.get(i).isReadOnly())
                        wl("            readOnlys = setBit(readOnlys, " + all.get(i).nameAsConstant() + "_INDEX);");

                wl("            _readOnlys = readOnlys;");
            }

            wl("        }");
        }

        wl();
        wl("        public Version(int length)" + (_gen.isCSharp() ? "" : " {"));

        if (_gen.isCSharp()) {
            wl("            : base(length)");
            wl("        {");
        } else
            wl("            super(length);");

        wl("        }");
        wl();

        String s = _gen.isCSharp() ? "GetField" : "field";

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + _gen.target().stringString() + " getFieldName(int index) {");
        wl("            return " + s + "Name(index);");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + "org.objectfabric.TType getFieldType(int index) {");
        wl("            return " + s + "Type(index);");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + _gen.target().objectString() + " getAsObject(int index) {");
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            ValueDef value = _valueSet.value(i);
            wl("                case " + value.nameAsConstant() + "_INDEX:");
            wl("                    return _" + value.Name + ";");
        }

        wl("                default:");
        wl("                    return " + (_gen.isCSharp() ? "base" : "super") + ".getAsObject(index);");
        wl("            }");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + "void setAsObject(int index, " + _gen.target().objectString() + " value) {");
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            ValueDef value = _valueSet.value(i);
            wl("                case " + value.nameAsConstant() + "_INDEX:");

            Immutable immutable = value.type().immutable();
            String rightPart;

            if (_gen.isJava() && immutable != null && immutable.isPrimitive() && !immutable.isBoxed())
                rightPart = "(" + value.type().cast(_gen) + "value)." + immutable.java() + "Value()";
            else
                rightPart = value.type().cast(_gen) + "value";

            wl("                    _" + value.Name + " = " + rightPart + ";");
            wl("                    break;");
        }

        wl("                default:");
        wl("                    " + (_gen.isCSharp() ? "base" : "super") + ".setAsObject(index, value);");
        wl("                    break;");
        wl("            }");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + "void merge(" + version() + " next) {");

        if (_valueSet.values().size() > 0) {
            wl("            " + _valueSet.actualName(_gen.objectModel()) + ".Version source = (" + _valueSet.actualName(_gen.objectModel()) + ".Version) next;");
            wl();
            wl("            if (source.hasBits()) {");
        }

        for (int i = 0; i < _valueSet.values().size(); i++) {
            if (i != 0)
                wl();

            ValueDef value = _valueSet.value(i);
            wl("                if (source.getBit(" + value.nameAsConstant() + "_INDEX))");
            wl("                    _" + value.Name + " = source._" + value.Name + ";");
        }

        if (_valueSet.values().size() > 0) {
            wl("            }");
            wl();
        }

        wl("            super.merge(next);");
        wl("        }");

        if (hasReadOnlys) {
            wl();

            if (_gen.isJava())
                wl("        @Override");

            if (_valueSet.lessOr32Fields(_gen.objectModel()))
                wl("        public" + _gen.target().overrideString() + "int getReadOnlys() {");
            else
                wl("        public" + _gen.target().overrideString() + "org.objectfabric.misc.Bits.Entry[] getReadOnlys() {");

            wl("            return _readOnlys;");
            wl("        }");
        }

        writeSerialization();
        wl("    }");
    }

    String version() {
        String version = "org.objectfabric.TObject.Version";

        if (_gen.isCSharp()) {
            if (_valueSet.lessOr32Fields(_gen.objectModel()))
                version = "ObjectFabric.TIndexed.Version32";
            else
                version = "ObjectFabric.TIndexed.VersionN";
        }

        return version;
    }

    String getClassId() {
        String id;

        if (_valueSet instanceof ClassDef) {
            int index = _gen.objectModel().allClasses().indexOf(_valueSet);
            id = _gen.objectModel().allClassIds().get(index);
        } else {
            int index = _gen.objectModel().allMethods().indexOf(_valueSet);
            id = _gen.objectModel().allMethodIds().get(index);
        }

        return id;
    }

    private void writeSerialization() {
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + "void writeWrite(" + (_gen.isCSharp() ? "object" : "org.objectfabric.Writer") + " writer, int index) {");

        String writerObj = _gen.isJava() ? "writer." : "";
        String writerArg = _gen.isJava() ? "" : "writer";
        String writerArgC = _gen.isJava() ? "" : "writer, ";

        wl("            if (" + writerObj + "interrupted(" + writerArg + "))");
        wl("                " + writerObj + "resume(" + writerArg + ");");
        wl();
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            ValueDef value = _valueSet.value(i);
            boolean fixedLength = value.type().fixedLength();

            wl("                case " + value.nameAsConstant() + "_INDEX: {");

            if (fixedLength) {
                String canWrite = null;

                Immutable immutable = value.type().immutable();

                if (immutable == null)
                    if (value.type().customUnderlyingImmutable() != null)
                        immutable = value.type().customUnderlyingImmutable();

                if (immutable != null)
                    canWrite = "canWrite" + immutable + "(" + writerArg + ")";

                if (value.type().isJavaEnum())
                    canWrite = "canWriteInteger(" + writerArg + ")";

                wl("                    if (!" + writerObj + canWrite + ") {");
                wl("                        " + writerObj + "interrupt(" + writerArgC + "null);");
                wl("                        return;");
                wl("                    }");
                wl();
            }

            String write;

            if (value.type().immutable() != null)
                write = "write" + value.type().immutable() + "(" + writerArgC + "_" + value.Name + ")";
            else if (value.type().custom() != null) {
                Immutable immutable = value.type().customUnderlyingImmutable();
                String cast = "(" + (_gen.isJava() ? immutable.java() : immutable.csharp()) + ")";
                write = "write" + immutable + "(" + writerArgC + cast + " _" + value.Name + ")";
            } else if (value.type().isTObject())
                write = "writeTObject(" + writerArgC + "_" + value.Name + ")";
            else if (value.type().isJavaEnum())
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
                wl();
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
        wl();
        wl("                    break;");
        wl("                }");
        wl("            }");
        wl("        }");
        wl();

        if (_gen.isJava())
            wl("        @Override");

        wl("        public" + _gen.target().overrideString() + "void readWrite(" + (_gen.isCSharp() ? "object" : "org.objectfabric.Reader") + " reader, int index, java.lang.Object[] versions) {");

        String readerObj = _gen.isJava() ? "reader." : "";
        String readerArg = _gen.isJava() ? "" : "reader";
        String readerArgC = _gen.isJava() ? "" : "reader, ";

        wl("            if (" + readerObj + "interrupted(" + readerArg + "))");
        wl("                " + readerObj + "resume(" + readerArg + ");");
        wl();
        wl("            switch (index) {");

        for (int i = 0; i < _valueSet.values().size(); i++) {
            ValueDef value = _valueSet.value(i);
            boolean fixedLength = value.type().fixedLength();

            wl("                case " + value.nameAsConstant() + "_INDEX: {");

            if (fixedLength) {
                String canRead = null;

                Immutable immutable = value.type().immutable();

                if (immutable == null)
                    if (value.type().customUnderlyingImmutable() != null)
                        immutable = value.type().customUnderlyingImmutable();

                if (immutable != null)
                    canRead = "canRead" + immutable + "(" + readerArg + ")";

                if (value.type().isJavaEnum())
                    canRead = "canReadInteger()";

                wl("                    if (!" + readerObj + canRead + ") {");
                wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
                wl("                        return;");
                wl("                    }");
                wl();
            }

            String left = value.type().fullName(_gen.target());
            String right;

            if (value.type().immutable() != null)
                right = readerObj + "read" + value.type().immutable() + "(" + readerArg + ")";
            else if (value.type().custom() != null) {
                Immutable immutable = value.type().customUnderlyingImmutable();
                String cast = "(" + value.type().custom() + ") ";
                right = cast + readerObj + "read" + immutable + "(" + readerArg + ")";
            } else if (value.type().isTObject()) {
                left = "java.lang.Object";
                right = readerObj + "readTObject(" + readerArg + ")";
            } else if (value.type().isJavaEnum())
                right = ObjectModelDef.formatAsConstant(value.type().fullName(Target.JAVA)) + TypeDef.ENUM_VALUES_ARRAY + "[" + readerObj + "readInteger(" + readerArg + ")]";
            else
                right = readerObj + "readObject(" + readerArg + ")";

            wl("                    " + left + " value = " + right + ";");

            if (!fixedLength) {
                wl();
                wl("                    if (" + readerObj + "interrupted(" + readerArg + ")) {");
                wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
                wl("                        return;");
                wl("                    }");
            }

            wl();

            if (value.type().canBeTObject()) {
                wl("                    for (int i = versions.length - 1; i >= 0; i--) {");
                wl("                        java.lang.Object v = value instanceof org.objectfabric.TObject[] ? ((org.objectfabric.TObject[]) value)[i] : value;");
                wl("                        ((Version) versions[i])._" + value.Name + " = (" + value.type().fullName(_gen.target()) + ") v;");
                wl("                    }");
            } else {
                wl("                    for (int i = versions.length - 1; i >= 0; i--)");
                wl("                        ((Version) versions[i])._" + value.Name + " = value;");
            }

            wl();
            wl("                    break;");
            wl("                }");
        }

        wl("                default: {");
        wl("                    " + (_gen.isCSharp() ? "base" : "super") + ".readWrite(reader, index, versions);");
        wl();
        wl("                    if (" + readerObj + "interrupted(" + readerArg + ")) {");
        wl("                        " + readerObj + "interrupt(" + readerArgC + "null);");
        wl("                        return;");
        wl("                    }");
        wl();
        wl("                    break;");
        wl("                }");
        wl("            }");
        wl("        }");
    }

    private void wl(String line) {
        _file.wl(line);
    }

    private void wl() {
        _file.wl();
    }

    private void tab() {
        _file.tab();
    }

    private void untab() {
        _file.untab();
    }
}
