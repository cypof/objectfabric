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

package part11.bench.generator;

import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;

public class Main {

    public static ObjectModelDef create() {
        ObjectModelDef model = new ObjectModelDef("BenchObjectModel");

        PackageDef p = new PackageDef("part11.bench.generated");
        model.Packages.add(p);

        GeneratedClassDef c = new GeneratedClassDef("MyClass");
        c.Fields.add(new FieldDef(int.class, "MyField"));
        p.Classes.add(c);

        return model;
    }

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = create();
        Generator generator = new Generator(model);
        generator.run("src");
    }
}
