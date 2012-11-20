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

import java.util.ArrayList;

@SuppressWarnings("rawtypes")
public class InternalsGenerator {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = new ObjectModelDef("DefaultObjectModel");
        model.setSkip();

        PackageDef pack = new PackageDef("org.objectfabric");
        model.Packages.add(pack);

        //

        ClassDef methodCall = new ClassDef("MethodCall");
        methodCall.setAbstract();
        methodCall.Public = false;
        pack.Classes.add(methodCall);

        FieldDef target = new FieldDef(TGenerated.class, "target");
        target.Readonly = true;
        methodCall.Fields.add(target);

        FieldDef isDone = new FieldDef(Immutable.BOOLEAN, "isDone");
        methodCall.Fields.add(isDone);

        FieldDef ret = new FieldDef(Object.class, "result_");
        ret.Public = "false";
        methodCall.Fields.add(ret);

        FieldDef error = new FieldDef(Immutable.STRING, "exception");
        methodCall.Fields.add(error);

        //

        Generator generator = new Generator(model);
        generator.copyright(PlatformGenerator.readCopyright());
        generator.run("../api/src/main/generated", Target.JAVA);

        // TIndexed
        generator.write(new FileWriterTIndexedBase(generator, "org.objectfabric"));

        //

        ArrayList<Class> classes = new ArrayList<Class>();

        for (Immutable c : Immutable.ALL)
            if (!c.isPrimitive() || c.isBoxed())
                classes.add(PlatformGenerator.forName(c.java()));

        classes.add(TObject.class);
        classes.add(Object.class);

        // TArray
        {
            for (Class c : classes)
                generator.write(new FileWriterTArray(generator, "org.objectfabric", c));

            for (Class c : classes)
                generator.write(new FileWriterTArrayVersion(generator, "org.objectfabric", c));
        }

        // JS
        {
            generator.folder("../js/src/main/java");

            for (Class c : classes)
                generator.write(new FileWriterTArrayJS(generator, "org.objectfabric.generated", c));
        }

        // CLR
        {
            generator.folder("../clr/Shared2/Generated");
            generator.target(Target.CSHARP);
            generator.write(new FileWriterTArrayVersionDotNet(generator));
        }
    }
}
