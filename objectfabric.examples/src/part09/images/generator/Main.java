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

package part09.images.generator;

import java.util.Arrays;

import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;
import com.objectfabric.generator.Target;

/**
 * This class generates the data model for the Images sample, which is made of a single
 * ImageInfo class. As it is very simple, we did not use XML as for other samples but
 * directly describe it using Java objects.
 */
public class Main {

    public static ObjectModelDef create() {
        ObjectModelDef model = new ObjectModelDef("ImagesObjectModel");

        PackageDef pack = new PackageDef("part09.images.generated");
        model.Packages.add(pack);

        GeneratedClassDef simple = new GeneratedClassDef("ImageInfo");
        simple.Fields.add(new FieldDef(String.class, "Url"));
        simple.Fields.add(new FieldDef(int.class, "Left"));
        simple.Fields.add(new FieldDef(int.class, "Top"));
        pack.Classes.add(simple);

        return model;
    }

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = create();
        Generator generator = new Generator(model);
        generator.run("src");
        byte[] uid = generator.getLastObjectModelUID();

        if (Arrays.asList(args).contains("/Android")) {
            generator.getObjectModel().Packages.get(0).Name = "examples.android.generated";
            generator.run("src/../../objectfabric.examples.android/src", uid);
        }

        if (Arrays.asList(args).contains("/CSharp")) {
            model.Packages.get(0).Name = "SampleImages";
            generator.run("src/../../of4dotnet.examples/SampleImages/Generated", Target.CSHARP, uid);
        }
    }
}
