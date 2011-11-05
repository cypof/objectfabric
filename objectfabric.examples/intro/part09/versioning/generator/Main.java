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

package part09.versioning.generator;

import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;

public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("intro/part09/versioning/generator/ObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("intro");
        
        // In case old model needs to be regenerated
        model = ObjectModelDef.fromXMLFile("intro/part09/versioning/generator/ObjectModel.v1.xml");
        model.Packages.get(0).Name = "part09.versioning.generated.v1";
        generator = new Generator(model);
        generator.run("intro");
    }
}
