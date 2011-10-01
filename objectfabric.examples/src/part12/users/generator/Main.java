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

package part12.users.generator;

import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;

public class Main {

    public static ObjectModelDef create() {
        ObjectModelDef model = new ObjectModelDef();

        PackageDef pack = new PackageDef("part12.users.generated");
        model.Packages.add(pack);

        GeneratedClassDef public_ = new GeneratedClassDef("PublicData");
        FieldDef publicField = new FieldDef(int.class, "data");
        public_.Fields.add(publicField);
        pack.Classes.add(public_);

        GeneratedClassDef private_ = new GeneratedClassDef("PrivateData");
        FieldDef privateField = new FieldDef(int.class, "data");
        private_.Fields.add(privateField);
        pack.Classes.add(private_);

        GeneratedClassDef user = new GeneratedClassDef("Profile");
        FieldDef pub = new FieldDef(new GeneratedClassDef("PublicData"), "pub");
        pub.Readonly = true;
        FieldDef prv = new FieldDef(new GeneratedClassDef("PrivateData"), "prv");
        prv.Readonly = true;
        user.Fields.add(pub);
        user.Fields.add(prv);
        pack.Classes.add(user);

        return model;
    }

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = create();
        Generator generator = new Generator(model);
        generator.run("src");
    }
}
