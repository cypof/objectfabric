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

import java.util.Arrays;

import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.Target;

/**
 * This classes invokes ObjectFabric code generator to generate an application's object
 * model. Object models can be imported from XML as in this example, imported from a
 * relational database (Check project objectfabric.examples.sql), or built directly using
 * code (Check example part04.store).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("src/part02/objectmodel/generator/ObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("src");

        if (Arrays.asList(args).contains("/CSharp")) {
            /*
             * This creates CSharp versions for the .NET port.
             */
            generator.run("../of4dotnet/Tests/SandBox/Generated", Target.CSHARP);
        }
    }
}
