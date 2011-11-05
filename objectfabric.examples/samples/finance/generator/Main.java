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

package finance.generator;

import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.Target;

public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("samples/finance/generator/ObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("samples");
        byte[] uid = generator.getLastObjectModelUID();

        model.Packages.get(0).Name = "finance.client.generated";
        generator.run("../of4gwt.samples.finance/src", Target.GWT, uid);
    }
}
