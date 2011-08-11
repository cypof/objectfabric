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

package com.objectfabric.misc;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import com.objectfabric.generator.ArgumentDef;
import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.MethodDef;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;
import com.objectfabric.generator.ReturnValueDef;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public class PlatformXML {

    public static String toXMLString(ObjectModelDef model, String schema) {
        JAXBContext context = getJAXBContext();

        try {
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.noNamespaceSchemaLocation", schema);
            StringWriter writer = new StringWriter();
            marshaller.marshal(model, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectModelDef fromXMLFile(String file) {
        try {
            return fromXML(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectModelDef fromXMLString(String xml) {
        return fromXML(new StringReader(xml));
    }

    public static ObjectModelDef fromXML(Reader xmlReader) {
        JAXBContext context = getJAXBContext();
        ObjectModelDef model;

        try {
            javax.xml.bind.Unmarshaller unmarshaller = context.createUnmarshaller();
            model = (ObjectModelDef) unmarshaller.unmarshal(xmlReader);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

        return model;
    }

    public static JAXBContext getJAXBContext() {
        ArrayList<Class> classes = new ArrayList<Class>();

        classes.add(ObjectModelDef.class);
        classes.add(PackageDef.class);
        classes.add(GeneratedClassDef.class);
        classes.add(FieldDef.class);
        classes.add(MethodDef.class);
        classes.add(ArgumentDef.class);
        classes.add(ReturnValueDef.class);

        try {
            return JAXBContext.newInstance(classes.toArray(new Class[classes.size()]));
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }
}
