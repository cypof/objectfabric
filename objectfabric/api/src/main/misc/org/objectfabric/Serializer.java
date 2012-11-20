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

/**
 * OF uses a custom binary format to describe changes on objects. It supports only simple
 * types by default ({@link Immutable}). A custom serializer can be implemented to
 * handle additional types, e.g. using Java or JSON serialization.
 */
public interface Serializer {

    byte[] serialize(Object object);

    Object deserialize(byte[] bytes);
}
