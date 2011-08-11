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

package com.objectfabric.examples.indexes.generator;

import com.objectfabric.TObject;
import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;

/**
 * Generates a transactional class for use by the example. The class is defined here using
 * code, but it is also possible to give the generator an XML definition as shown in other
 * examples.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = new ObjectModelDef();

        PackageDef p = new com.objectfabric.generator.PackageDef("com.objectfabric.examples.indexes.generated");
        model.Packages.add(p);

        GeneratedClassDef stored = new GeneratedClassDef("StoredClass");
        stored.Fields.add(new FieldDef(String.class, "text"));
        stored.Fields.add(new FieldDef(TObject.class, "reference"));
        stored.Fields.add(new FieldDef(int.class, "int"));
        p.Classes.add(stored);

        Generator generator = new Generator(model);
        generator.run("./src");
    }
}
