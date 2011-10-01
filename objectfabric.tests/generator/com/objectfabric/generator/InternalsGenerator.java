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

import java.util.ArrayList;
import java.util.EnumSet;

import com.objectfabric.ImmutableClass;
import com.objectfabric.Privileged;
import com.objectfabric.TObject;
import com.objectfabric.generator.Generator.Flag;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.PlatformFile;

class InternalsGenerator extends Privileged {

    public static void main(String[] args) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("generator/com/objectfabric/generator/DefaultObjectModel.xml");
        Generator generator = new Generator(model);
        generator.setCopyright(PlatformFile.readCopyright());
        generator.run("../objectfabric/generated", EnumSet.noneOf(Flag.class));

        {
            FileWriterTIndexedBase writer = new FileWriterTIndexedBase(generator, "com.objectfabric");
            generator.write(writer);
        }

        //

        {
            ArrayList<Class> classes = new ArrayList<Class>();

            for (ImmutableClass c : ImmutableClass.ALL)
                if (!c.isPrimitive() || c.isBoxed())
                    classes.add(PlatformClass.forName(c.getJava()));

            classes.add(TObject.class);
            classes.add(Object.class);

            for (Class c : classes) {
                FileWriterTArray writer = new FileWriterTArray(generator, "com.objectfabric", c);
                generator.write(writer);
            }

            for (Class c : classes) {
                FileWriterTArrayVersion writer = new FileWriterTArrayVersion(generator, "com.objectfabric", c);
                generator.write(writer);
            }
        }

        //

        generator.setFolder("../of4dotnet/VS/CSharp/Generated");
        generator.setTarget(Target.CSHARP);

        for (int i = 0; i < model.getAllPackages().size(); i++) {
            PackageDef p = model.getAllPackages().get(i);

            for (int j = 0; j < p.Classes.size(); j++) {
                if ("LazyMap".equals(p.Classes.get(j).Name)) {
                    FileGenerator file = new FileGeneratorClass(generator, p.Classes.get(j));
                    file.generate();
                    generator.replace("ObjectFabric.TKeyed ", "com.objectfabric.TKeyed ");
                    generator.replace("abstract partial class LazyMapBase", "public partial class LazyDictionary<K, V>");
                    generator.replace("LazyMapBase", "LazyDictionary");
                    generator.replace("getDefaultMethodExecutor_objectfabric()", "(System.Threading.Tasks.TaskScheduler) getDefaultMethodExecutor_objectfabric()");
                    generator.replace("getCompletedFuture_objectfabric", "TGeneratedFields32.getCompletedFuture_objectfabric");
                    generator.replace("Fetch(object", "Fetch(K");
                    generator.replace("FetchAsync(object", "FetchAsync(K");
                    generator.replace("Task<object>", "Task<V>");
                    generator.replace("LocalMethodCall<object>", "TGeneratedFields32.LocalMethodCall<V>");
                    generator.replace("object result_ = null;", "V result_ = default(V);");
                    generator.replace("object key = ", "K key = (K) ");
                    generator.replace("startRead_objectfabric", "(ObjectFabric.Transaction) startRead_objectfabric");
                    generator.replace("startWrite_objectfabric", "(ObjectFabric.Transaction) startWrite_objectfabric");
                    generator.replace("ObjectFabric.MethodCall", "com.objectfabric.MethodCall");
                    generator.replace("ObjectFabric.TObject", "com.objectfabric.TObject");
                    generator.replace("protected override", "protected internal override");
                    generator.writeCacheToFile(file);
                }
            }
        }

        {
            FileWriterTArrayForDotNet writer = new FileWriterTArrayForDotNet(generator);
            generator.write(writer);
        }

        {
            FileWriterTArrayVersionForDotNet writer = new FileWriterTArrayVersionForDotNet(generator);
            generator.write(writer);
        }
    }
}
