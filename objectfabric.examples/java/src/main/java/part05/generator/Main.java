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

package part05.generator;

import org.objectfabric.ClassDef;
import org.objectfabric.FieldDef;
import org.objectfabric.Generator;
import org.objectfabric.ObjectModelDef;
import org.objectfabric.Target;

/**
 * This classes invokes ObjectFabric code generator to generate an application's object
 * model. Here we generate models for two platforms, a Java model for the JVM & Google Web
 * Toolkit samples and a C# model for the .NET sample.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        /*
         * Object models can be imported from XML as in this example, imported from a
         * relational database (Check SQL sample), or built using code like below.
         */
        ObjectModelDef model = ObjectModelDef.fromXMLFile("src/main/java/part05/generator/ObjectModel.xml");

        /*
         * To define or customize a model programmatically, use *Def types:
         */
        ClassDef stored = new ClassDef("MyClass");
        stored.Fields.add(new FieldDef(int.class, "field"));
        stored.Fields.add(new FieldDef(int.class, "field2"));
        stored.Fields.add(new FieldDef(String.class, "text"));
        model.Packages.get(0).Classes.add(stored);

        /*
         * Then call the generator, specifying a folder.
         */
        Generator generator = new Generator(model);
        generator.run("src/main/java");

        /*
         * Generates the C# version. ObjectFabric identifies models through a UID, so
         * reusing the same UID allows objects from different platforms to synchronize
         * with each other.
         */
        byte[] uid = part05.generated.MyObjectModel.uid();
        generator.run("../csharp/05 ObjectModel/Generated", Target.CSHARP, uid);

        /*
         * Versioning. In case an old model needs to be regenerated you can force its UID
         * to the one randomly chosen at the time (C.f. MyObjectModel.java), so that it
         * keeps the same identity. Make sure that the model structure is exactly the same
         * or it wont be able to deserialize the old data.
         */
        model = ObjectModelDef.fromXMLFile("src/main/java/part05/generator/ObjectModel.v1.xml");
        model.Packages.get(0).Name = "part05.generated.v1";
        generator = new Generator(model);
        byte[] oldUID = { -25, 2, 71, -53, 60, -117, 94, 104, -81, 117, -82, -66, -37, -71, 22, 28 };
        generator.run("src/main/java", oldUID);
    }
}
