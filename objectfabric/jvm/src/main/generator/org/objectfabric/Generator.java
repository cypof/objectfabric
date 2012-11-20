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

public class Generator extends GeneratorBase {

    static {
        JVMPlatform.loadClass();
    }

    public Generator() {
        this(null);
    }

    public Generator(ObjectModelDef model) {
        super(model);
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Generator generator = new Generator();
        int result = generator.parseArgs(args);

        if (result == 0) {
            ObjectModelDef model = ObjectModelDef.fromXMLFile(generator.xml());
            generator.setObjectModel(model);
            generator.run(Platform.get().newUID(), null);
        }

        return result;
    }
}
