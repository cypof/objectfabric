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

package com.objectfabric.vm.generator;

import com.objectfabric.generator.ArgumentDef;
import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.MethodDef;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;
import com.objectfabric.generator.ReturnValueDef;

public class Main {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = getObjectModel();
        Generator generator = new Generator(model);
        generator.run("./distributed");
    }

    public static ObjectModelDef getObjectModel() {
        ObjectModelDef model = new ObjectModelDef("MethodsObjectModel");

        PackageDef root = new PackageDef("com.objectfabric.vm.generated");
        model.Packages.add(root);

        GeneratedClassDef ref = new GeneratedClassDef("MethodRef");
        ref.Fields.add(new FieldDef(String.class, "text"));
        root.Classes.add(ref);

        GeneratedClassDef simpleMethod = new GeneratedClassDef("SimpleMethod");
        simpleMethod.Fields.add(new FieldDef(String.class, "text"));
        simpleMethod.Fields.add(new FieldDef(int.class, "int"));
        simpleMethod.Fields.add(new FieldDef(int.class, "int2"));
        simpleMethod.Fields.add(new FieldDef(ref, "simple"));
        root.Classes.add(simpleMethod);

        MethodDef method = new MethodDef("method", null);
        method.ReturnValue = new ReturnValueDef(String.class);
        method.Arguments.add(new ArgumentDef(String.class, "sql"));
        method.Arguments.add(new ArgumentDef(simpleMethod, "eg"));
        simpleMethod.Methods.add(method);

        MethodDef progress = new MethodDef("progress", null);
        progress.Arguments.add(new ArgumentDef(int.class, "state"));
        simpleMethod.Methods.add(progress);

        return model;
    }
}
