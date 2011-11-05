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

package part02.objectmodel.generator;

import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.Target;

/**
 * This classes invokes ObjectFabric code generator to generate an application's object
 * model. Object models can be imported from XML as in this example, imported from a
 * relational database (Check project objectfabric.examples.sql), or built directly using
 * code (Check example part04.store).
 * <nl>
 * In this example we generate three versions of the model, a Java version, a different
 * Java model compatible with Google Web Toolkit to write web applications, and a C#
 * version to write .NET applications.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("intro/part02/objectmodel/generator/ObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("intro");

        /*
         * This step is important, copy the UID that was generated for the Java version
         * and pass it when generating the other versions. ObjectFabric identifies models
         * by their UID, so reusing the same UID allows all versions of a model to be
         * synchronized with each other.
         */
        byte[] uid = part02.objectmodel.generated.MyObjectModel.getUID();

        /*
         * Generate a model for Google Web Toolkit.
         */
        model.Packages.get(0).Name = "objectmodel.client.generated";
        model.Packages.get(1).Name = "objectmodel.client.generated.subPackage";
        generator.run("../of4gwt.intro02.objectmodel/src", Target.GWT, uid);

        /*
         * This creates CSharp code for the .NET version.
         */
        generator.run("../of4dotnet/Tests/SandBox/Generated", Target.CSHARP, uid);
    }
}
