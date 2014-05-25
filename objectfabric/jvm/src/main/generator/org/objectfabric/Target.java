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

public abstract class Target {

    static {
        JVMPlatform.loadClass();
    }

    public static final Target JAVA = new Java();

    public static final Target CSHARP = new CSharp();

    //

    abstract String extension();

    abstract String overrideString();

    abstract String booleanString();

    abstract String extendsString();

    abstract String superString();

    abstract String stringString();

    abstract String classString();

    abstract String objectString();

    //

    List<FileGenerator> writeFiles(GeneratorBase generator, String folder, byte[] uid, ModelVisitor visitor) {
        generator.folder(folder);
        generator.target(this);
        return generator.run(uid, visitor);
    }

    /**
     * @param generator
     * @param file
     */
    void onWritingFile(GeneratorBase generator, FileGenerator file) {
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
