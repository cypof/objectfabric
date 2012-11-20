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

class DotNETReadWriteGenerator {

    public static void main(String[] args) throws Exception {
        JVMPlatform.loadClass();

        for (Immutable c : Immutable.ALL)
            w(c);
    }

    static void r(Immutable c) {
        if (c.fixedLength()) {
            System.out.println("    new public bool canRead" + c + "()");
            System.out.println("    {");
            System.out.println("        return base.canRead" + c + "();");
            System.out.println("    }");
            System.out.println("");
        }

        System.out.println("    new public " + c.csharp() + " read" + c + "()");
        System.out.println("    {");
        System.out.println("        return base.read" + c + "();");
        System.out.println("    }");
        System.out.println("");
    }

    static void w(Immutable c) {
        if (c.fixedLength()) {
            System.out.println("    new public bool canWrite" + c + "()");
            System.out.println("    {");
            System.out.println("        return base.canWrite" + c + "();");
            System.out.println("    }");
            System.out.println("");
        }

        System.out.println("    new public void write" + c + "(" + c.csharp() + " value)");
        System.out.println("    {");
        System.out.println("        base.write" + c + "(value);");
        System.out.println("    }");
        System.out.println("");
    }
}
