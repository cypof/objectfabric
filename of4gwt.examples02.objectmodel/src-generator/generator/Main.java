
package generator;

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

import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.Target;

public class Main {

    /**
     * This generates the same set of files as the Java version of the ObjectModel demo,
     * but compatible with GWT.
     */
    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("../objectfabric.examples/src/part02/objectmodel/generator/ObjectModel.xml");
        model.Packages.get(0).Name = "objectmodel.client.generated";
        model.Packages.get(1).Name = "objectmodel.client.generated.subPackage";
        Generator generator = new Generator(model);

        /*
         * This step is important, copy the UID that was generated for the Java version of
         * this demo. ObjectFabric will identify the two models as the same and will
         * replicate Java objects to and from GWT.
         */
        byte[] uid = part02.objectmodel.generated.MyObjectModel.getUID();
        generator.run("src", Target.GWT, uid);
    }
}
