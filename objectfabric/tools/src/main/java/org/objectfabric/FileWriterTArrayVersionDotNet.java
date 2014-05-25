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

class FileWriterTArrayVersionDotNet extends FileGenerator {

    public FileWriterTArrayVersionDotNet(Generator generator) {
        super(generator, "", "TArrayVersion");
    }

    @Override
    protected void body() {
        String nl = JVMPlatform.LINE_SEPARATOR;
        StringBuilder template = FileWriterTArray.getTemplate("src/main/java/org/objectfabric/" + Name + "Template.java");
        Utils.replace(template, "float", "T");
        Utils.replace(template, "class TArrayVersionTemplate extends TIndexed.VersionN {", "sealed class TArrayVersion<T> : TIndexed.VersionN {");
        Utils.replace(template, "TArrayVersionTemplate(int length) {", "public TArrayVersion(int length)");
        Utils.replace(template, "super(length);", ": base(length) {");
        Utils.replace(template, "value != 0 /* Default */", "value != null && !value.Equals(default(T))");
        Utils.replace(template, ".length", ".Length");
        Utils.replace(template, "super.", "base.");
        Utils.replace(template, "    @Override" + nl + "    public", "    public override");
        Utils.replace(template, "    @Override" + nl + "    ", "    internal override ");
        Utils.replace(template, "Float", "T");
        Utils.replace(template, "package org.objectfabric;", "namespace org.objectfabric {");
        Utils.replace(template, "// .NET", "");
        Utils.replace(template, "Template", "<T>");
        Utils.replace(template, " final ", " ");
        Utils.replace(template, "0 /* Default */", "default(T)");
        Utils.replace(template, "boolean", "bool");
        Utils.replace(template, "java.lang.Object", "object");
        Utils.replace(template, "@SuppressWarnings", "// @SuppressWarnings");

        StringBuilder wb = new StringBuilder();
        StringBuilder rb = new StringBuilder();

        wl(wb, "        if (IS_TOBJECT) {");
        wl(wb, "            writer.writeTObject((TObject) (object) get(index));");
        wl(wb, "");
        wl(wb, "            if (writer.interrupted()) {");
        wl(wb, "                writer.interrupt(null);");
        wl(wb, "                return;");
        wl(wb, "            }");

        //

        wl(rb, "        if (IS_TOBJECT) {");
        wl(rb, "            TObject[] objects = reader.readTObject();");
        wl(rb, "");
        wl(rb, "            if (reader.interrupted()) {");
        wl(rb, "                reader.interrupt(null);");
        wl(rb, "                return;");
        wl(rb, "            }");
        wl(rb, "");
        wl(rb, "            for (int i = versions.Length - 1; i >= 0; i--)");
        wl(rb, "                ((TArrayVersion<T>) versions[i]).set(index, (T) (object) objects[i]);");

        for (Immutable immutable : Immutable.ALL) {
            if (!immutable.isPrimitive() || !immutable.isBoxed()) {
                wl(wb, "        } else if (typeof(T) == typeof(" + immutable.csharp() + ")) {");

                if (immutable.fixedLength()) {
                    wl(wb, "            if (!writer.canWrite" + immutable + "()) {");
                    wl(wb, "                writer.interrupt(null);");
                    wl(wb, "                return;");
                    wl(wb, "            }");
                    wl(wb, "");
                }

                wl(wb, "            TArrayVersion<" + immutable.csharp() + "> version = (TArrayVersion<" + immutable.csharp() + ">) (object) this;");
                wl(wb, "            writer.write" + immutable + "(version.get(index));");

                if (!immutable.fixedLength()) {
                    wl(wb, "");
                    wl(wb, "            if (writer.interrupted()) {");
                    wl(wb, "                writer.interrupt(null);");
                    wl(wb, "                return;");
                    wl(wb, "            }");
                }

                //

                wl(rb, "        } else if (typeof(T) == typeof(" + immutable.csharp() + ")) {");

                if (immutable.fixedLength()) {
                    wl(rb, "            if (!reader.canRead" + immutable + "()) {");
                    wl(rb, "                reader.interrupt(null);");
                    wl(rb, "                return;");
                    wl(rb, "            }");
                    wl(rb, "");
                }

                wl(rb, "            " + immutable.csharp() + " value = reader.read" + immutable + "();");

                if (!immutable.fixedLength()) {
                    wl(rb, "");
                    wl(rb, "            if (reader.interrupted()) {");
                    wl(rb, "                reader.interrupt(null);");
                    wl(rb, "                return;");
                    wl(rb, "            }");
                }

                wl(rb, "");
                wl(rb, "            for (int i = versions.Length - 1; i >= 0; i--)");
                wl(rb, "                ((TArrayVersion<" + immutable.csharp() + ">) versions[i]).set(index, value);");
            }
        }

        wl(wb, "        } else {");
        wl(wb, "            UnknownObjectSerializer.write(writer, get(index));");
        wl(wb, "");
        wl(wb, "            if (writer.interrupted()) {");
        wl(wb, "                writer.interrupt(null);");
        wl(wb, "                return;");
        wl(wb, "            }");
        wl(wb, "        }");

        //

        wl(rb, "        } else {");
        wl(rb, "            object o = UnknownObjectSerializer.read(reader);");
        wl(rb, "");
        wl(rb, "            if (reader.interrupted()) {");
        wl(rb, "                reader.interrupt(null);");
        wl(rb, "                return;");
        wl(rb, "            }");
        wl(rb, "");
        wl(rb, "            for (int i = versions.Length - 1; i >= 0; i--) {");
        wl(rb, "                object value = o;");
        wl(rb, "");
        wl(rb, "                if (value is TObject[])");
        wl(rb, "                    value = ((TObject[]) value)[i];");
        wl(rb, "");
        wl(rb, "                ((TArrayVersion<T>) versions[i]).set(index, (T) value);");
        wl(rb, "            }");
        wl(rb, "        }");

        Utils.replace(template, "        // Write" + nl, wb.toString());
        Utils.replace(template, "        // Read" + nl, rb.toString());
        write(template);
    }

    static void wl(StringBuilder sb, String line) {
        sb.append(line);
        sb.append(JVMPlatform.LINE_SEPARATOR);
    }
}
