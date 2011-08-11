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

import com.objectfabric.generator.Generator;
import com.objectfabric.misc.Utils;

class FileWriterTArrayVersionForDotNet extends FileWriterTArrayForDotNet {

    public FileWriterTArrayVersionForDotNet(Generator generator) {
        super(generator, "TArrayVersion");
    }

    @Override
    protected void replaceSpecific(StringBuilder template) {
        Utils.replace(template, "float", "T");
        Utils.replace(template, "class TArrayVersionTemplate extends TIndexedNVersion {", "class TArrayVersion<T> : TIndexedNVersion {");
        Utils.replace(template, "TType[] _genericParameters = new TType[] { new TType(ImmutableClass.FLOAT) };", "TType[] _genericParameters;");
        Utils.replace(template, "public TArrayVersionTemplate(TObject.Version shared, int length) {", "public TArrayVersion(TObject.Version shared, int length)");
        Utils.replace(template, "super(shared, length);", ": base(shared, length) {");
        Utils.replace(template, "    @Override" + Utils.NEW_LINE + "    public", "    public override");
        Utils.replace(template, "value != 0 /* Default */", "value != null && !value.Equals(default(T))");
        Utils.replace(template, ".length", ".Length");
        Utils.replace(template, "super.", "base.");
        Utils.replace(template, "instanceof", "is");
        Utils.replace(template, " Object", " object");
        Utils.replace(template, "(Object", "(object");

        StringBuilder sb = new StringBuilder();
        wl(sb, "if(_genericParameters == null)");
        wl(sb, "            _genericParameters = new TType[] { ObjectFabric.TType.From( typeof( T ) ) };");
        wl(sb, "");
        Utils.replace(template, "return _genericParameters;", sb.toString() + "        return _genericParameters;");

        // StringBuilder rb = new StringBuilder();
        //
        // for (ImmutableClass immutable : ImmutableClass.ALL) {
        // StringBuilder wb = new StringBuilder();
        // ImmutableClass primitive = immutable.isPrimitive() ? immutable.getNonBoxed() :
        // immutable;
        //
        // if (immutable.fixedLength()) {
        // wl(wb, "        if (!writer.canWrite" + primitive + "()) {");
        // wl(wb, "            writer.interrupt(null);");
        // wl(wb, "            return;");
        // wl(wb, "        }");
        // wl(wb, "");
        // }
        //
        // wl(wb, "        writer.write" + primitive + "(get(index));");
        //
        // if (!immutable.fixedLength()) {
        // wl(wb, "");
        // wl(wb, "        if (writer.interrupted()) {");
        // wl(wb, "            writer.interrupt(null);");
        // wl(wb, "            return;");
        // wl(wb, "        }");
        // }
        //
        // Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());
        //
        // StringBuilder rb = new StringBuilder();
        //
        // if (immutable.fixedLength()) {
        //
        // wl(rb, "        if (!reader.canRead" + primitive + "()) {");
        // wl(rb, "            reader.interrupt(null);");
        // wl(rb, "            return;");
        // wl(rb, "        }");
        // wl(rb, "");
        // }
        //
        // wl(rb, "        set(index, reader.read" + primitive + "());");
        //
        // if (!immutable.fixedLength()) {
        // wl(rb, "");
        // wl(rb, "        if (reader.interrupted()) {");
        // wl(rb, "            reader.interrupt(null);");
        // wl(rb, "            return;");
        // wl(rb, "        }");
        // }
        //
        // Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        // } else if (_type == TObject.class) {
        // StringBuilder wb = new StringBuilder();
        // wl(wb, "        writer.writeTObject(get(index));");
        // wl(wb, "");
        // wl(wb, "        if (writer.interrupted()) {");
        // wl(wb, "            writer.interrupt(null);");
        // wl(wb, "            return;");
        // wl(wb, "        }");
        // Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());
        //
        // StringBuilder rb = new StringBuilder();
        // wl(rb, "        TObject object = reader.readTObject();");
        // wl(rb, "");
        // wl(rb, "        if (reader.interrupted()) {");
        // wl(rb, "            reader.interrupt(null);");
        // wl(rb, "            return;");
        // wl(rb, "        }");
        // wl(rb, "");
        // wl(rb, "        set(index, object);");
        // Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        // } else {
        // StringBuilder wb = new StringBuilder();
        // wl(wb, "        UnknownObjectSerializer.write(writer, get(index), -1);");
        // wl(wb, "");
        // wl(wb, "        if (writer.interrupted()) {");
        // wl(wb, "            writer.interrupt(null);");
        // wl(wb, "            return;");
        // wl(wb, "        }");
        // Utils.replace(template, "        // Write" + Utils.NEW_LINE, wb.toString());
        //
        // StringBuilder rb = new StringBuilder();
        // wl(rb, "        Object object = UnknownObjectSerializer.read(reader);");
        // wl(rb, "");
        // wl(rb, "        if (reader.interrupted()) {");
        // wl(rb, "            reader.interrupt(null);");
        // wl(rb, "            return;");
        // wl(rb, "        }");
        // wl(rb, "");
        // wl(rb, "        set(index, object);");
        // Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
        // } }
        //
        // Utils.replace(template, "        // Read" + Utils.NEW_LINE, rb.toString());
    }

    private static void wl(StringBuilder sb, String line) {
        sb.append(line);
        sb.append(Utils.NEW_LINE);
    }
}
