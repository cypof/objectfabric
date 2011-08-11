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

package part04.store.generator;

import com.objectfabric.TObject;
import com.objectfabric.TSet;
import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;
import com.objectfabric.generator.TypeDef;

/**
 * Generates a transactional class for use by the tutorials. The class is defined here
 * using code, but it is also possible to give the generator an XML definition as shown in
 * other examples.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = new ObjectModelDef();

        PackageDef p = new com.objectfabric.generator.PackageDef("part04.store.generated");
        model.Packages.add(p);

        GeneratedClassDef stored = new GeneratedClassDef("StoredClass");
        stored.Fields.add(new FieldDef(String.class, "text"));
        stored.Fields.add(new FieldDef(TObject.class, "reference"));
        TypeDef type = new TypeDef(TSet.class);
        type.addGenericsParameter(String.class);
        stored.Fields.add(new FieldDef(type, "set"));
        p.Classes.add(stored);

        Generator generator = new Generator(model);
        generator.run("src");
    }
}
