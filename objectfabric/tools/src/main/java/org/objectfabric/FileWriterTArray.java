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

import java.io.File;
import java.io.FileReader;

class FileWriterTArray extends FileGenerator {

    protected final String _name;

    protected final Class<?> _type;

    public FileWriterTArray(Generator generator, String packag, Class<?> type) {
        this(generator, "TArray", packag, type);
    }

    public FileWriterTArray(Generator generator, String name, String packag, Class<?> type) {
        super(generator, packag, name + getName(type));

        _name = name;
        _type = type;
    }

    protected static String getName(Class<?> type) {
        if (type == byte[].class)
            return "Binary";

        return type != Object.class ? type.getSimpleName() : "";
    }

    @Override
    protected void body() {
        StringBuilder template = getTemplate("src/main/java/org/objectfabric/" + _name + "Template.java");
        replaceSpecific(template);
        replaceCommon(template);
        write(template);
    }

    static StringBuilder getTemplate(String path) {
        try {
            File file = new File(path);
            FileReader reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            return new StringBuilder(new String(chars));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void replaceSpecific(StringBuilder template) {
        if (_type == TObject.class)
            Utils.replace(template, "Immutable.FLOAT.type()", "TObject.TYPE");

        if (_type == Object.class) {
            Utils.replace(template, ", Immutable.FLOAT.type()", "");
            Utils.replace(template, "_genericParameters = new TType[] { Immutable.FLOAT.type() };", "_genericParameters = null;");
            Utils.replace(template, "implements Iterable<Float>", "implements Iterable<E> ");
            Utils.replace(template, "public final float get", "public final E get");
            Utils.replace(template, "return value;", "return (E) value;");
            Utils.replace(template, "set(int index, float value)", "set(int index, E value)");
            Utils.replace(template, "java.util.Iterator<Float>", "java.util.Iterator<E>");
            Utils.replace(template, "public Float next()", "public E next()");
            Utils.replace(template, "CAN_BE_TOBJECT = false;", "CAN_BE_TOBJECT = true;");
        }

        Immutable immutable = Immutable.parse(_type.getName());

        if (immutable != null) {
            Utils.replace(template, "/* generic_declaration */", " ");
            Immutable nonBoxed = immutable;

            if (immutable.isPrimitive())
                nonBoxed = immutable.nonBoxed();

            StringBuilder constant = new StringBuilder(nonBoxed.toString().toUpperCase());

            for (int i = nonBoxed.toString().length() - 1; i > 0; i--)
                if (Character.isUpperCase(nonBoxed.toString().charAt(i)))
                    constant = constant.insert(i, '_');

            Utils.replace(template, "Immutable.FLOAT", "Immutable." + constant);
        } else if (_type == TObject.class) {
            Utils.replace(template, "float", "E");
            Utils.replace(template, "Float", "E");
            Utils.replace(template, "/* generic_declaration */", "<E extends TObject> ");
        } else if (_type == Object.class) {
            Utils.replace(template, "float", "Object");
            Utils.replace(template, "Float", "Object");
            Utils.replace(template, "/* generic_declaration */", "<E> ");
        }

        if (_type != TObject.class) {
            Utils.replace(template, "/* generic start */", "/* generic start ");
            Utils.replace(template, "/* generic end */", " generic end */");
        }

        if (_type == TObject.class) {
            Utils.replace(template, "IS_TOBJECT = false;", "IS_TOBJECT = true;");
            Utils.replace(template, "CAN_BE_TOBJECT = false;", "CAN_BE_TOBJECT = true;");
        }
    }

    private final void replaceCommon(StringBuilder template) {
        Immutable immutable = Immutable.parse(_type.getName());
        Immutable nonBoxed = null;

        if (immutable != null) {
            nonBoxed = immutable;

            if (immutable.isPrimitive()) {
                nonBoxed = immutable.nonBoxed();
                Utils.replace(template, "float", nonBoxed.java());
                Utils.replace(template, "Float", _type.getSimpleName());
            } else {
                if (_type != byte[].class) {
                    Utils.replace(template, "float", _type.getName());
                    Utils.replace(template, "Float", _type.getName());
                } else {
                    Utils.replace(template, "float", "byte[]");
                    Utils.replace(template, "Float", "byte[]");
                }
            }

            Utils.replace(template, "Template", getName(_type));
        } else if (_type == TObject.class) {
            Utils.replace(template, "Template", "TObject");
        } else if (_type == Object.class) {
            Utils.replace(template, "Template", "");
        }

        if (nonBoxed != null)
            Utils.replace(template, "0 /* Default */", nonBoxed.defaultString());
        else
            Utils.replace(template, "0 /* Default */", "null");
    }
}
