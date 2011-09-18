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

package of4gwt.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import of4gwt.OF.Serializer;

/**
 * OF uses its own binary format for serializing objects. In some cases, and if platform
 * interoperability is not necessary, it can be handy to use Java serialization instead,
 * e.g. so serialize Shiro sessions.
 */
public class JavaSerializer implements Serializer {

    public boolean canSerialize(Object object) {
        return true;
    }

    public byte[] serialize(Object object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return bos.toByteArray();
    }

    public Object deserialize(byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream in = new ObjectInputStream(bis);
            Object object = in.readObject();
            in.close();
            return object;
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}