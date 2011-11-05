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

import java.util.EnumSet;

import com.objectfabric.generator.Generator.Flag;
import com.objectfabric.misc.List;

public abstract class Target {

    public static final Target JAVA = new Java();

    public static final Target GWT = new GWT();

    public static final Target CSHARP = new CSharp();

    //

    abstract String extension();

    abstract String overrideString();

    abstract String booleanString();

    abstract String getMaxShort();

    abstract String extendsString();

    abstract String superString();

    abstract String stringString();

    abstract String classString();

    abstract String objectString();

    //

    List<FileGenerator> writeFiles(Generator generator, String folder, byte[] uid, EnumSet<Flag> flags, Visitor visitor) {
        generator.setFolder(folder);
        generator.setTarget(this);
        generator.setFlags(flags != null ? flags : EnumSet.noneOf(Flag.class));
        return generator.run(uid, visitor);
    }

    /**
     * @param generator
     * @param file
     */
    void onWritingFile(Generator generator, FileGenerator file) {
    }

    //

    private static class Java extends Target {

        @Override
        public String extension() {
            return ".java";
        }

        @Override
        public String overrideString() {
            return " ";
        }

        @Override
        public String booleanString() {
            return "boolean";
        }

        @Override
        public String getMaxShort() {
            return "Short.MAX_VALUE";
        }

        @Override
        public String extendsString() {
            return "extends";
        }

        @Override
        public String superString() {
            return "super";
        }

        @Override
        public String stringString() {
            return "java.lang.String";
        }

        @Override
        public String classString() {
            return "java.lang.Class";
        }

        @Override
        public String objectString() {
            return "java.lang.Object";
        }
    }

    private static class GWT extends Java {

        @Override
        List<FileGenerator> writeFiles(Generator generator, String folder, byte[] uid, EnumSet<Flag> flags, Visitor visitor) {
            if (flags == null)
                flags = EnumSet.of(Flag.GENERATE_ONLY_ASYNCHRONOUS_METHODS);
            else
                flags.add(Flag.GENERATE_ONLY_ASYNCHRONOUS_METHODS);

            return super.writeFiles(generator, folder, uid, flags, visitor);
        }

        @Override
        void onWritingFile(Generator generator, FileGenerator file) {
            generator.replace("com.objectfabric.misc.AsyncCallback", "com.google.gwt.user.client.rpc.AsyncCallback");
            generator.replace("com.objectfabric.", "of4gwt.");
            generator.replace("java.util.concurrent.Future", "of4gwt.misc.Future");
            generator.replace("java.util.concurrent.Executor", "of4gwt.misc.Executor");
            generator.replace("java.util.concurrent.ExecutionException", "of4gwt.misc.ExecutionException");
            generator.replace("java.lang.InterruptedException", "of4gwt.misc.InterruptedException");

            super.onWritingFile(generator, file);
        }
    }

    private static class CSharp extends Target {

        @Override
        public String extension() {
            return ".cs";
        }

        @Override
        public String overrideString() {
            return " override ";
        }

        @Override
        public String booleanString() {
            return "bool";
        }

        @Override
        public String getMaxShort() {
            return "short.MaxValue";
        }

        @Override
        public String extendsString() {
            return ":";
        }

        @Override
        public String superString() {
            return "base";
        }

        @Override
        public String stringString() {
            return "string";
        }

        @Override
        public String classString() {
            return "System.Type";
        }

        @Override
        public String objectString() {
            return "object";
        }
    }
}
