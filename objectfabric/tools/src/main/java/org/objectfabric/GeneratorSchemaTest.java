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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.objectfabric.Generator;
import org.objectfabric.ObjectModelDef;

public class GeneratorSchemaTest {

    public static void main(String[] args) throws Exception {
        File tempFile = marshall();
        unmarshall(tempFile);
    }

    public static File marshall() throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile("../objectfabric.examples/src/part3/objectmodel/generator/ObjectModel.xml");
        String xml = model.toXMLString(Generator.SCHEMA_FILE);

        File temp = File.createTempFile("ObjectFabricTempFile", null);
        FileWriter writer = new FileWriter(temp);
        writer.write(xml);
        writer.close();

        return temp;
    }

    public static void unmarshall(File file) throws Exception {
        ObjectModelDef model = ObjectModelDef.fromXMLFile(file.getPath());
        Generator generator = new Generator(model);
        File temp;

        try {
            temp = File.createTempFile("ObjectFabricTempFolder", null);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        temp.delete();
        temp.mkdir();

        generator.run(temp.getAbsolutePath());
    }
}