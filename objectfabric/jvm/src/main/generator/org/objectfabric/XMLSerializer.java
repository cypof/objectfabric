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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

class XMLSerializer {

    static {
        JVMPlatform.loadClass();
    }

    static String toXMLString(ObjectModelDef model, String schema) {
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

    static ObjectModelDef fromXMLFile(String file) {
        try {
            return fromXML(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static ObjectModelDef fromXMLString(String xml) {
        return fromXML(new StringReader(xml));
    }

    static ObjectModelDef fromXML(Reader xmlReader) {
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

    static JAXBContext getJAXBContext() {
        ArrayList<Class> classes = new ArrayList<Class>();

        classes.add(ObjectModelDef.class);
        classes.add(PackageDef.class);
        classes.add(ClassDef.class);
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
