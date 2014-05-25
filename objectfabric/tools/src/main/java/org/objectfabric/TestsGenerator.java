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

package org.objectfabric;

public class TestsGenerator {

    public static void main(String[] args) throws Exception {
        Generator generator = new Generator();
        generator.copyright(PlatformGenerator.readCopyright());

        // Simple

        ObjectModelDef model = new ObjectModelDef("SimpleObjectModel");
        PackageDef root = new PackageDef("org.objectfabric.generated");
        model.Packages.add(root);

        ClassDef simple = new ClassDef("SimpleClass");
        simple.Fields.add(new FieldDef(String.class, "text"));
        simple.Fields.add(new FieldDef(int.class, "int0"));
        simple.Fields.add(new FieldDef(int.class, "int1"));
        simple.Fields.add(new FieldDef(int.class, "int2"));
        simple.Fields.add(new FieldDef(int.class, "int3"));
        simple.Fields.add(new FieldDef(TMap.class, "map"));
        root.Classes.add(simple);

        generator.setObjectModel(model);
        generator.run("../jvm/src/test/java");

        // Limits

        model = new ObjectModelDef("LimitsObjectModel");
        root = new PackageDef("org.objectfabric.generated");
        model.Packages.add(root);

        ClassDef limit32 = new ClassDef("Limit32");

        for (int i = 0; i < 25; i++)
            limit32.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limit32);

        ClassDef limit32_max = new ClassDef("Limit32_max");

        for (int i = 0; i < 32; i++)
            limit32_max.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limit32_max);

        //

        ClassDef limitN_min = new ClassDef("LimitN_min");

        for (int i = 0; i < 257; i++)
            limitN_min.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limitN_min);

        ClassDef limitN = new ClassDef("LimitN");

        for (int i = 0; i < 300; i++)
            limitN.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limitN);

        //

        generator.setObjectModel(model);
        generator.run("../jvm/src/test/java");

        // Types

        model = new ObjectModelDef("TypesObjectModel");
        root = new PackageDef("org.objectfabric.generated");
        model.Packages.add(root);

        ClassDef types = new ClassDef("TypesClass");

        for (int i = 0; i < Immutable.COUNT; i++)
            types.Fields.add(new FieldDef(Immutable.ALL.get(i), "field" + i));

        TypeDef type = new TypeDef(TArrayTObject.class);
        type.addGenericsParameter(types);
        types.Fields.add(new FieldDef(type, "array"));
        root.Classes.add(types);

        generator.setObjectModel(model);
        generator.run("../jvm/src/test/java");

        // Methods

        // model = new ObjectModelDef("MethodsObjectModel");
        // root = new PackageDef("org.objectfabric.generated");
        // model.Packages.add(root);
        //
        // ClassDef ref = new ClassDef("MethodRef");
        // ref.Fields.add(new FieldDef(String.class, "text"));
        // root.Classes.add(ref);
        //
        // ClassDef methods = new ClassDef("MethodsClass");
        // methods.Fields.add(new FieldDef(String.class, "text"));
        // methods.Fields.add(new FieldDef(int.class, "int"));
        // methods.Fields.add(new FieldDef(int.class, "int2"));
        // methods.Fields.add(new FieldDef(ref, "simple"));
        // root.Classes.add(methods);
        //
        // MethodDef method = new MethodDef("method", null);
        // method.ReturnValue = new ReturnValueDef(String.class);
        // method.Arguments.add(new ArgumentDef(String.class, "sql"));
        // method.Arguments.add(new ArgumentDef(methods, "eg"));
        // methods.Methods.add(method);
        //
        // MethodDef progress = new MethodDef("progress", null);
        // progress.Arguments.add(new ArgumentDef(int.class, "state"));
        // methods.Methods.add(progress);
        //
        // generator.setObjectModel(model);
        // generator.run("../jvm/src/test/java");
    }
}
