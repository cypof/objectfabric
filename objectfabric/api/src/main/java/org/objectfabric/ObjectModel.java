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
 * Represents an application's object model. An implementation of this class is generated
 * as the same time as the classes of your application when using ObjectFabric's class
 * generator.
 */
public abstract class ObjectModel {

    private static final PlatformConcurrentMap<UID, ObjectModel> _models = new PlatformConcurrentMap<UID, ObjectModel>();

    protected ObjectModel() {
    }

    protected abstract byte[] uid_();

    @SuppressWarnings("rawtypes")
    protected java.lang.Class getClass(int classId, TType[] genericParameters) {
        throw new IllegalStateException();
    }

    protected TObject createInstance(Resource resource, int classId, TType[] genericParameters) {
        throw new IllegalStateException();
    }

    protected abstract String objectFabricVersion();

    static final ObjectModel get(byte[] uid) {
        return _models.get(new UID(uid));
    }

    protected static void register(ObjectModel model) {
        if (Debug.ENABLED) {
            Platform platform = Platform.get();

            if (platform != null)
                Debug.assertion(!(platform.simpleClassName(model).equals("DefaultObjectModel")));
        }

        if (!TObject.OBJECT_FABRIC_VERSION.equals(model.objectFabricVersion()))
            throw new IllegalArgumentException(Strings.WRONG_OBJECTFABRIC_VERSION);

        _models.putIfAbsent(new UID(model.uid_()), model);
    }

    // Accessors

    protected static void arraycopy(byte[] a, byte[] b) {
        Platform.arraycopy(a, 0, b, 0, b.length);
    }
}
