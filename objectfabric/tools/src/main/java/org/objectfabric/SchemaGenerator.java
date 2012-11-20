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

import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

import org.objectfabric.Generator;
import org.objectfabric.XMLSerializer;

/**
 * You don't need this class for normal usage. It generates the schema to which XML object
 * model definitions must conform to.
 */
public class SchemaGenerator {

    public static void main(String[] args) throws Exception {
        JAXBContext context = XMLSerializer.getJAXBContext();

        context.generateSchema(new SchemaOutputResolver() {

            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
                return new StreamResult(Generator.SCHEMA_FILE);
            }
        });
    }
}