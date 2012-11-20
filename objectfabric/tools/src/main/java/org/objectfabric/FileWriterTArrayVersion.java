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

class FileWriterTArrayVersion extends FileWriterTArray {

    public FileWriterTArrayVersion(Generator generator, String packag, Class<?> type) {
        super(generator, "TArrayVersion", packag, type);
    }

    @Override
    protected void replaceSpecific(StringBuilder template) {
        String nl = JVMPlatform.LINE_SEPARATOR;

        if (_type == TObject.class) {
            Utils.replace(template, "float", "TObject");
            Utils.replace(template, "Float", "TObject");
        }

        if (_type == Object.class) {
            Utils.replace(template, "float", "Object");
            Utils.replace(template, "Float", "Object");
        }

        if (_type == byte[].class)
            Utils.replace(template, "new float[arrayLength]", "new byte[arrayLength][]");

        //

        Immutable immutable = Immutable.parse(_type.getName());

        if (immutable != null) {
            StringBuilder wb = new StringBuilder();
            Immutable primitive = immutable.isPrimitive() ? immutable.nonBoxed() : immutable;

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

            Utils.replace(template, "        // Write" + nl, wb.toString());

            StringBuilder rb = new StringBuilder();

            if (immutable.fixedLength()) {

                wl(rb, "        if (!reader.canRead" + primitive + "()) {");
                wl(rb, "            reader.interrupt(null);");
                wl(rb, "            return;");
                wl(rb, "        }");
                wl(rb, "");
            }

            wl(rb, "        " + primitive.java() + " value = reader.read" + primitive + "();");

            if (!immutable.fixedLength()) {
                wl(rb, "");
                wl(rb, "        if (reader.interrupted()) {");
                wl(rb, "            reader.interrupt(null);");
                wl(rb, "            return;");
                wl(rb, "        }");
            }

            wl(rb, "");
            wl(rb, "        for (int i = versions.length - 1; i >= 0; i--)");
            wl(rb, "            ((TArrayVersionTemplate) versions[i]).set(index, value);");

            Utils.replace(template, "        // Read" + nl, rb.toString());
        } else if (_type == TObject.class) {
            StringBuilder wb = new StringBuilder();
            wl(wb, "        writer.writeTObject(get(index));");
            wl(wb, "");
            wl(wb, "        if (writer.interrupted()) {");
            wl(wb, "            writer.interrupt(null);");
            wl(wb, "            return;");
            wl(wb, "        }");
            Utils.replace(template, "        // Write" + nl, wb.toString());

            StringBuilder rb = new StringBuilder();
            wl(rb, "        TObject[] objects = reader.readTObject();");
            wl(rb, "");
            wl(rb, "        if (reader.interrupted()) {");
            wl(rb, "            reader.interrupt(null);");
            wl(rb, "            return;");
            wl(rb, "        }");
            wl(rb, "");
            wl(rb, "        for (int i = versions.length - 1; i >= 0; i--)");
            wl(rb, "            ((TArrayVersionTemplate) versions[i]).set(index, objects[i]);");
            Utils.replace(template, "        // Read" + nl, rb.toString());
        } else {
            StringBuilder wb = new StringBuilder();
            wl(wb, "        UnknownObjectSerializer.write(writer, get(index));");
            wl(wb, "");
            wl(wb, "        if (writer.interrupted()) {");
            wl(wb, "            writer.interrupt(null);");
            wl(wb, "            return;");
            wl(wb, "        }");
            Utils.replace(template, "        // Write" + nl, wb.toString());

            StringBuilder rb = new StringBuilder();
            wl(rb, "        Object object = UnknownObjectSerializer.read(reader);");
            wl(rb, "");
            wl(rb, "        if (reader.interrupted()) {");
            wl(rb, "            reader.interrupt(null);");
            wl(rb, "            return;");
            wl(rb, "        }");
            wl(rb, "");
            wl(rb, "        for (int i = versions.length - 1; i >= 0; i--) {");
            wl(rb, "            Object value = object;");
            wl(rb, "");
            wl(rb, "            if (value instanceof TObject[])");
            wl(rb, "                value = ((TObject[]) value)[i];");
            wl(rb, "");
            wl(rb, "            ((TArrayVersionTemplate) versions[i]).set(index, value);");
            wl(rb, "        }");
            Utils.replace(template, "        // Read" + nl, rb.toString());
        }
    }

    private static void wl(StringBuilder sb, String line) {
        sb.append(line);
        sb.append(JVMPlatform.LINE_SEPARATOR);
    }
}
