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

import java.util.ArrayList;

public class SerializationTestGenerator extends FileGenerator {

    private final boolean _reader, _unknown;

    public SerializationTestGenerator(Generator generator, String packg, boolean reader, boolean unknown) {
        super(generator, packg, getName(reader, unknown));

        _reader = reader;
        _unknown = unknown;
    }

    private static String getName(boolean reader, boolean unknown) {
        return (reader ? "SerializationTestReader" : "SerializationTestWriter") + (unknown ? "Unknown" : "");
    }

    protected ArrayList<String> getValues(Immutable c) {
        ArrayList<String> values = new ArrayList<String>();
        values.add(c.defaultString());

        if (c == Immutable.BOOLEAN)
            values.add("true");
        else if (c == Immutable.BOOLEAN_BOXED) {
            if (g().isJava()) {
                values.add("Boolean.TRUE");
                values.add("Boolean.FALSE");
            } else {
                values.add("true");
                values.add("false");
            }
        } else if (c.isPrimitive() && !c.isBoxed()) {
            if (g().isJava()) {
                values.add(c.boxed().java() + ".MAX_VALUE");
                values.add(c.boxed().java() + ".MIN_VALUE");
            } else {
                values.add(c.csharp() + ".MaxValue");
                values.add(c.csharp() + ".MaxValue");
            }
        } else if (c.isPrimitive() && !c.isBoxed()) {
            if (g().isJava()) {
                values.add(c.boxed().java() + ".MAX_VALUE");
                values.add(c.boxed().java() + ".MIN_VALUE");
            } else {
                values.add(c.nonBoxed().csharp() + ".MaxValue");
                values.add(c.nonBoxed().csharp() + ".MaxValue");
            }
        } else if (c == Immutable.STRING) {
            values.add("\"\"");
            values.add("\"\\u0000\"");
            values.add("\"\\u00FF\"");
            values.add("\"\\u0AFF\"");
            values.add("\"\\u7FFF\"");
            values.add("\"\\uFFFF\"");
            values.add("\"ffqsdfqfezghrtghrgrfgzefzeqfzeqfqzefqzefqzefqzeefqzefqzefsdqfsdghfgzegqzefqsdfqzefqezfqzefqze'\"");
        } else if (c == Immutable.BIG_INTEGER) {
            String type = g().isJava() ? "new java.math.BigInteger" : "System.Numerics.BigInteger.Parse";
            values.add(type + "(\"0\")");
            values.add(type + "(\"-0\")");
            values.add(type + "(\"45\")");
            values.add(type + "(\"-45\")");
            values.add(type + "(\"1237987\")");
            values.add(type + "(\"-1237987\")");
            values.add(type + "(\"1237987898798797464864181688684513518313131813113513\")");
            values.add(type + "(\"-1237987898798797464864181688684513518313131813113513\")");
        } else if (c == Immutable.DECIMAL) {
            String start = g().isJava() ? "new java.math.BigDecimal( \"" : "";
            String end = g().isJava() ? "\")" : "m";
            values.add(start + "0" + end);
            values.add(start + "-0" + end);
            values.add(start + "45" + end);
            values.add(start + "-45" + end);
            values.add(start + "123798789879879.456464" + end);
            values.add(start + "-123798789879879.456464" + end);
            values.add(start + "0.000000000000078" + end);
            values.add(start + "-0.000000000000078" + end);
            values.add(start + "2.0e5" + end);
            values.add(start + "-2.0e5" + end);
            values.add(start + "789.046544654846468789486e13" + end);
            values.add(start + "-789.046544654846468789486e13" + end);
            values.add(start + "789.046544654846468789486e-13" + end);
            values.add(start + "-789.046544654846468789486e-13" + end);
        } else if (c == Immutable.DATE) {
            if (g().isJava()) {
                values.add("new java.util.Date(4558621531843L)");
                values.add("new java.util.Date(0)");
            } else {
                values.add("new System.DateTime( 4558621531843L * 10000L + 621355968000000000L, System.DateTimeKind.Utc )");
                values.add("System.DateTime.Parse( \"1/1/1970 00:00:00\", null, System.Globalization.DateTimeStyles.AssumeUniversal )");
            }
        } else if (c == Immutable.BINARY) {
            values.add("new byte[] { 45, 88 }");
        }

        return values;
    }

    @Override
    protected void header() {
        if (g().isJava()) {
            copyright();

            wl();
            wl("package org.objectfabric;");
            wl();

            warning();
        } else {
            warning();

            wl("namespace org.objectfabric {");
            wl();
        }
    }

    @Override
    protected void body() {
        final String name = getName(_reader, _unknown);

        wl("public class " + name + " " + g().target().extendsString() + " org.objectfabric.SerializationTest." + (_reader ? "TestReader" : "TestWriter") + " {");
        wl();
        wl("    private int _index;");
        wl();

        if (g().isJava())
            wl("    @Override");

        wl("    public" + g().target().overrideString() + "void run() {");
        wl("        for (;;) {");
        wl("            switch (_index) {");

        int index = 0;

        for (Immutable c : Immutable.ALL) {
            for (String value : getValues(c)) {
                String type = g().isJava() ? c.java() : c.csharp();

                wl("                case " + index++ + ": {");

                if (!_unknown) {
                    if (_reader) {
                        String equals = value.equals("null") || (c.isPrimitive() && !c.isBoxed()) ? " == (" : "." + (g().isJava() ? "e" : "E") + "quals(";

                        if (c.fixedLength()) {
                            wl("                    if (!canRead" + c + "())");
                            wl("                        return;");
                            wl();
                            String cast = g().isCSharp() ? "(" + c.csharp() + ")" : "";
                            String read = "read" + c + "()";
                            wl("                    org.objectfabric.Debug.assertAlways((" + cast + read + ")" + equals + value + "));");
                        } else {
                            if (c != Immutable.BINARY)
                                wl("                    " + type + " value = read" + c + "();");
                            else
                                wl("                    byte[] value = readBinary();");

                            wl();
                            wl("                    if (interrupted())");
                            wl("                        return;");
                            wl();

                            if (c != Immutable.BINARY)
                                wl("                    org.objectfabric.Debug.assertAlways(value" + equals + value + "));");
                            else
                                wl("                    org.objectfabric.Debug.assertAlways(java.util.Arrays.equals(value, " + value + "));");
                        }
                    } else {
                        if (c.fixedLength()) {
                            wl("                    if (!canWrite" + c + "())");
                            wl("                        return;");
                            wl();
                            wl("                    write" + c + "(" + value + ");");
                        } else {
                            wl("                    write" + c + "(" + value + ");");
                            wl();
                            wl("                    if (interrupted())");
                            wl("                        return;");
                            wl();
                        }
                    }
                } else if (!c.isPrimitive() || c.isBoxed()) {
                    if (_reader) {
                        if (c != Immutable.BINARY)
                            wl("                    " + type + " value = (" + type + ") org.objectfabric.UnknownObjectSerializer.read(this);");
                        else
                            wl("                    byte[] value = (byte[]) org.objectfabric.UnknownObjectSerializer.read(this);");

                        wl();
                        wl("                    if (interrupted())");
                        wl("                        return;");
                        wl();

                        if (c != Immutable.BINARY) {
                            String equals = value.equals("null") ? " == (" : "." + (g().isJava() ? "e" : "E") + "quals(";
                            wl("                    org.objectfabric.Debug.assertAlways(value" + equals + value + "));");
                        } else
                            wl("                    org.objectfabric.Debug.assertAlways(java.util.Arrays.equals(value, " + value + "));");
                    } else {
                        wl("                    org.objectfabric.UnknownObjectSerializer.write(this, " + value + ");");
                        wl();
                        wl("                    if (interrupted())");
                        wl("                        return;");
                    }
                }

                wl("                    break;");
                wl("                }");
            }
        }

        wl("                default:");
        wl("                    return;");
        wl("            }");
        wl();
        wl("            _index++;");
        wl("        }");
        wl("    }");
        wl();

        if (_reader) {
            if (g().isJava())
                wl("    @Override");

            wl("    public" + g().target().overrideString() + g().target().booleanString() + " isDone() {");
            wl("        return _index == " + index + ";");
            wl("    }");
        }

        wl("}");

        if (g().isCSharp())
            wl("}");
    }

    public static void main(String[] args) {
        Generator generator = new Generator(null);
        String packg = "org.objectfabric";
        generator.copyright(PlatformGenerator.readCopyright());

        generator.folder("../jvm/src/test/serialization");
        generator.target(Target.JAVA);
        generator.write(new SerializationTestGenerator(generator, packg, true, false));
        generator.write(new SerializationTestGenerator(generator, packg, false, false));
        generator.write(new SerializationTestGenerator(generator, packg, true, true));
        generator.write(new SerializationTestGenerator(generator, packg, false, true));

        generator.folder("../clr/JUnit/Generated");
        generator.target(Target.CSHARP);
        generator.write(new SerializationTestGenerator(generator, packg, true, false));
        generator.write(new SerializationTestGenerator(generator, packg, false, false));
        generator.write(new SerializationTestGenerator(generator, packg, true, true));
        generator.write(new SerializationTestGenerator(generator, packg, false, true));
    }
}
