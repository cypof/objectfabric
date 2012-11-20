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

class TTypeGenerator {

    public static void main(String[] args) throws Exception {
        JVMPlatform.loadClass();
        boolean first = true;

        for (Immutable c : Immutable.ALL) {
            if (!c.isPrimitive() || !c.isBoxed()) {
                System.out.println((first ? "" : "else ") + "if( type == typeof( " + c.csharp() + " ) )");
                System.out.println("result = (TType) org.objectfabric.Immutable." + c.toString().toUpperCase() + ".getType();");
                first = false;
            }
        }

        System.out.println();
        System.out.println();

        for (Immutable c : Immutable.ALL) {
            if (!c.isPrimitive() || !c.isBoxed()) {
                System.out.println("case org.objectfabric.Immutable." + c.toString().toUpperCase() + "_INDEX:");
                System.out.println("return typeof( " + c.csharp() + " );");
            }
        }
    }
}
