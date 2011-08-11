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

package com.objectfabric.generator;

import com.objectfabric.TArrayTObject;
import com.objectfabric.misc.PlatformFile;

public class Main {

    public static void main(String[] args) throws Exception {
        Generator generator = new Generator();
        generator.setCopyright(PlatformFile.readCopyright());

        // Simple

        ObjectModelDef model = new ObjectModelDef("SimpleObjectModel");
        PackageDef root = new PackageDef("com.objectfabric.generated");
        model.Packages.add(root);

        GeneratedClassDef simple = new GeneratedClassDef("SimpleClass");
        simple.Fields.add(new FieldDef(String.class, "text"));
        simple.Fields.add(new FieldDef(int.class, "int0"));
        simple.Fields.add(new FieldDef(int.class, "int1"));
        simple.Fields.add(new FieldDef(int.class, "int2"));
        simple.Fields.add(new FieldDef(int.class, "int3"));
        root.Classes.add(simple);

        generator.setObjectModel(model);
        generator.run("./stm");

        // Limits

        model = new ObjectModelDef("LimitsObjectModel");
        root = new PackageDef("com.objectfabric.generated");
        model.Packages.add(root);

        GeneratedClassDef limit32 = new GeneratedClassDef("Limit32");

        for (int i = 0; i < 25; i++)
            limit32.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limit32);

        GeneratedClassDef limit32_max = new GeneratedClassDef("Limit32_max");

        for (int i = 0; i < 32; i++)
            limit32_max.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limit32_max);

        //

        GeneratedClassDef limitN_min = new GeneratedClassDef("LimitN_min");

        for (int i = 0; i < 257; i++)
            limitN_min.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limitN_min);

        GeneratedClassDef limitN = new GeneratedClassDef("LimitN");

        for (int i = 0; i < 300; i++)
            limitN.Fields.add(new FieldDef(int.class, "int" + i));

        root.Classes.add(limitN);

        //

        generator.setObjectModel(model);
        generator.run("./stm");

        // References

        model = new ObjectModelDef("ReferencesObjectModel");
        root = new PackageDef("com.objectfabric.generated");
        model.Packages.add(root);

        GeneratedClassDef refs = new GeneratedClassDef("ReferencesClass");
        refs.Fields.add(new FieldDef(String.class, "text"));
        refs.Fields.add(new FieldDef(int.class, "int"));
        refs.Fields.add(new FieldDef(int.class, "int2"));
        refs.Fields.add(new FieldDef(refs, "ref"));
        refs.Fields.add(new FieldDef(refs, "ref2"));
        TypeDef type = new TypeDef(TArrayTObject.class);
        type.addGenericsParameter(refs);
        refs.Fields.add(new FieldDef(type, "array"));
        root.Classes.add(refs);

        generator.setObjectModel(model);
        generator.run("./stm");

        // Persistence

        model = new ObjectModelDef("PersistenceObjectModel");
        root = new PackageDef("com.objectfabric.generated");
        model.Packages.add(root);

        GeneratedClassDef persistence = new GeneratedClassDef("PersistenceClass");
        persistence.Fields.add(new FieldDef(String.class, "text"));
        persistence.Fields.add(new FieldDef(int.class, "int"));
        persistence.Fields.add(new FieldDef(long.class, "long"));
        persistence.Fields.add(new FieldDef(Double.class, "double"));
        persistence.Fields.add(new FieldDef(Object.class, "object"));
        persistence.Fields.add(new FieldDef(byte[].class, "bytes"));
        root.Classes.add(persistence);

        generator.setObjectModel(model);
        generator.run("./persistence");
    }
}
