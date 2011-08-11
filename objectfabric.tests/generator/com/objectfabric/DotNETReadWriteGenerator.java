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

package com.objectfabric;

import com.objectfabric.ImmutableClass;
import com.objectfabric.Privileged;

class DotNETReadWriteGenerator extends Privileged {

    public static void main(String[] args) throws Exception {
        for (ImmutableClass c : ImmutableClass.ALL) {
            if (c.fixedLength()) {
                System.out.println("    public bool canRead" + c + "(object reader)");
                System.out.println("    {");
                System.out.println("        return ((Reader) reader).canRead" + c + "();");
                System.out.println("    }");
                System.out.println("");
            }

            System.out.println("    public " + c.getCSharp() + " read" + c + "(object reader)");
            System.out.println("    {");
            System.out.println("        return ((Reader) reader).read" + c + "();");
            System.out.println("    }");
            System.out.println("");

            if (c.fixedLength()) {
                System.out.println("    public bool canWrite" + c + "(object writer)");
                System.out.println("    {");
                System.out.println("        return ((Writer) writer).canWrite" + c + "();");
                System.out.println("    }");
                System.out.println("");
            }

            System.out.println("    public void write" + c + "(object writer, " + c.getCSharp() + " value)");
            System.out.println("    {");
            System.out.println("        ((Writer) writer).write" + c + "(value);");
            System.out.println("    }");
            System.out.println("");
        }
    }
}
