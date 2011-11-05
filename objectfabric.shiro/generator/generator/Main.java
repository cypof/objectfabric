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

package generator;

import com.objectfabric.Privileged;
import com.objectfabric.ShiroObjectModel;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.Target;

class Main extends Privileged {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("generator/generator/ShiroObjectModel.xml");
        Generator generator = new Generator(model);
        generator.run("generated", ShiroObjectModel.getUID());

        // GWT version

        for (GeneratedClassDef c : model.Packages.get(0).Classes)
            c.Abstract = false;

        model.Packages.get(0).Name = "of4gwt";
        generator.run("../of4gwt.shiro/src", Target.GWT, generator.getLastObjectModelUID());
    }
}
