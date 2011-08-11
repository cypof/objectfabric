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
import com.objectfabric.TObject;
import com.objectfabric.generator.Generator;
import com.objectfabric.misc.Utils;

class FileWriterTArrayVersion extends FileWriterTArray {

    public FileWriterTArrayVersion(Generator generator, String packag, Class type) {
        super(generator, "TArrayVersion", packag, type);
    }

    @Override
    protected void replaceSpecific(StringBuilder template) {
        if (_type == TObject.class) {
            Utils.replace(template, "float", "TObject");
            Utils.replace(template, "Float", "TObject");
        }

        if (_type == Object.class) {
            Utils.replace(template, "float", "Object");
            Utils.replace(template, "Float", "Object");
            Utils.replace(template, "new TType[] { new TType(ImmutableClass.FLOAT) }", "null");
        }

        if (_type == TObject.class)
            Utils.replace(template, "_genericParameters = new TType[] { new TType(ImmutableClass.FLOAT) };", "_genericParameters = new TType[] { TObject.TYPE };");
        else
            Utils.replace(template, "TType[] _genericParameters", "private static final TType[] _genericParameters");

        if (_type == byte[].class)
            Utils.replace(template, "new float[arrayLength]", "new byte[arrayLength][]");

        //

        ImmutableClass immutable = ImmutableClass.parse(_type.getName());

        if (immutable != null) {
            StringBuilder wb = new StringBuilder();
            ImmutableClass primitive = immutable.isPrimitive() ? immutable.getNonBoxed() : immutable;

            if (immutable.fixedLength()) {
                wl(wb, "        if (!writer.canWrite" + primitive + "()) {");
                wl(wb, "            writer.interrupt(null);");
                wl(wb, "            return;");
                wl(wb, "        }");
                wl(wb, "");
            }

            wl(wb, "        writer.write" + primitive + "(get(index));");

            if (!immutable.fixedLength()) {
                wl(wb, "");
                wl(wb, "        if (writer.interrupted()) {");
                wl(wb, "            writer.interrupt(null);");
                wl(wb, "            return;");
                wl(wb, "        }");
            }

            Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());

            StringBuilder rb = new StringBuilder();

            if (immutable.fixedLength()) {

                wl(rb, "        if (!reader.canRead" + primitive + "()) {");
                wl(rb, "            reader.interrupt(null);");
                wl(rb, "            return;");
                wl(rb, "        }");
                wl(rb, "");
            }

            wl(rb, "        set(index, reader.read" + primitive + "());");

            if (!immutable.fixedLength()) {
                wl(rb, "");
                wl(rb, "        if (reader.interrupted()) {");
                wl(rb, "            reader.interrupt(null);");
                wl(rb, "            return;");
                wl(rb, "        }");
            }

            Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        } else if (_type == TObject.class) {
            StringBuilder wb = new StringBuilder();
            wl(wb, "        writer.writeTObject(get(index));");
            wl(wb, "");
            wl(wb, "        if (writer.interrupted()) {");
            wl(wb, "            writer.interrupt(null);");
            wl(wb, "            return;");
            wl(wb, "        }");
            Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());

            StringBuilder rb = new StringBuilder();
            wl(rb, "        TObject object = reader.readTObject();");
            wl(rb, "");
            wl(rb, "        if (reader.interrupted()) {");
            wl(rb, "            reader.interrupt(null);");
            wl(rb, "            return;");
            wl(rb, "        }");
            wl(rb, "");
            wl(rb, "        set(index, object);");
            Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        } else {
            StringBuilder wb = new StringBuilder();
            wl(wb, "        UnknownObjectSerializer.write(writer, get(index), -1);");
            wl(wb, "");
            wl(wb, "        if (writer.interrupted()) {");
            wl(wb, "            writer.interrupt(null);");
            wl(wb, "            return;");
            wl(wb, "        }");
            Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());

            StringBuilder rb = new StringBuilder();
            wl(rb, "        Object object = UnknownObjectSerializer.read(reader);");
            wl(rb, "");
            wl(rb, "        if (reader.interrupted()) {");
            wl(rb, "            reader.interrupt(null);");
            wl(rb, "            return;");
            wl(rb, "        }");
            wl(rb, "");
            wl(rb, "        set(index, object);");
            Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        }
    }

    private static void wl(StringBuilder sb, String line) {
        sb.append(line);
        sb.append(Utils.NEW_LINE);
    }
}
