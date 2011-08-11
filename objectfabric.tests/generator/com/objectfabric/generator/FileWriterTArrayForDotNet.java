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

import com.objectfabric.misc.Utils;

class FileWriterTArrayForDotNet extends FileGenerator {

    public FileWriterTArrayForDotNet(Generator generator) {
        this(generator, "TArray");
    }

    public FileWriterTArrayForDotNet(Generator generator, String name) {
        super(generator, "", name);
    }

    @Override
    protected void body() {
        StringBuilder template = FileWriterTArray.getTemplate(Name);
        replaceSpecific(template);
        replaceCommon(template);
        write(template);
    }

    protected void replaceSpecific(StringBuilder template) {
        Utils.replace(template, "float", "T");
        Utils.replace(template, "abstract class TArrayTemplate/* generic_declaration */extends TIndexed implements Iterable<Float>", "public class TArray<T> : TIndexed");
        Utils.replace(template, "public TArrayTemplate(int length) {", "public TArray(int length)");
        Utils.replace(template, "public TArrayTemplate(Transaction trunk, int length) {", "public TArray(Transaction trunk, int length)");
        Utils.replace(template, "this(Transaction.getDefaultTrunk(), length);", ": this(Transaction.getDefaultTrunk(), length) {");
        String base = ": base( IS_TOBJECT ? new TArrayVersion<TObject>( null, length ) : ( CAN_BE_TOBJECT ? (Version) new TArrayVersion<object>( null, length ) : new TArrayVersion<T>( null, length ) ), trunk ) {";
        Utils.replace(template, "super(new TArrayVersionTemplate(null, length), trunk);", base);
        Utils.replace(template, "/* generic start */", "/* generic start ");
        Utils.replace(template, "/* generic end */", " generic end */");
        Utils.replace(template, "/* iterator start */", "/* iterator start");
        Utils.replace(template, "/* iterator end */", "iterator end */");
        Utils.replace(template, "IndexOutOfBoundsException", "System.ArgumentOutOfRangeException");
        Utils.replace(template, "java.util.Iterator<Float>", "java.util.Iterator");
        Utils.replace(template, "private final int ", "private readonly int ");
        Utils.replace(template, "implements", ":");
        Utils.replace(template, "public static final TType TYPE = new TType(DefaultObjectModel.getInstance(), -1, new TType(ImmutableClass.FLOAT));", "");
        // Avoid trying to store TObject.Version in shared's array of T
        Utils.replace(template, "Template/* TObject */", "<TObject> ");
        Utils.replace(template, "Template/* Object */", "<object> ");

        Utils.replace(template, "final boolean IS_TOBJECT = false;", "readonly bool IS_TOBJECT = ObjectFabric.TType.IsTObject( typeof( T ) );");
    }

    private final void replaceCommon(StringBuilder template) {
        Utils.replace(template, "final boolean CAN_BE_TOBJECT = false;", "readonly bool CAN_BE_TOBJECT = ObjectFabric.TType.CanBeTObject( typeof( T ) );");
        Utils.replace(template, "Float", "T");
        Utils.replace(template, "package com.objectfabric;", "namespace com.objectfabric {");
        Utils.replace(template, "// End (for .NET)", "}");
        Utils.replace(template, "Template", "<T>");
        Utils.replace(template, " final ", " ");
        Utils.replace(template, "0 /* Default */", "default(T)");
        Utils.replace(template, "boolean", "bool");
        Utils.replace(template, "    @SuppressWarnings(\"cast\")" + Utils.NEW_LINE, "");
    }
}
