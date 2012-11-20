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

package sample_quotes.generator;

import org.objectfabric.Generator;
import org.objectfabric.ObjectModelDef;

public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("src/main/java/sample_quotes/generator/ObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("src/main/java");

        // GWT
        // model.Packages.get(0).Name = "quotes.client.generated";
        // generator.run("../org.objectfabric.  TODO  /src/main/java",
        // generator.getObjectModelUID());

        // .NET
    }
}
