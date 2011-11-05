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

package of4gwt;

import of4gwt.TObject.UserTObject;
import of4gwt.TObject.UserTObject.SystemClass;

/**
 * Represents an application's object model. An implementation of this class is generated
 * as the same time as the classes of your application when using ObjectFabric's class
 * generator.
 */
public abstract class ObjectModel extends UserTObject implements SystemClass {

    @SuppressWarnings("hiding")
    public static final TType TYPE = new TType(DefaultObjectModel.COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID);

    protected ObjectModel(TObject.Version shared) {
        super(shared, Transaction.getLocalTrunk());

        getSharedVersion_objectfabric().setUnion(new DescriptorForUID(this, Record.EMPTY), true);
    }

    /**
     * @param classId
     * @param genericParameters
     */
    protected java.lang.Class getClass(int classId, TType[] genericParameters) {
        throw new IllegalStateException();
    }

    /**
     * @param trunk
     * @param classId
     * @param genericParameters
     */
    protected UserTObject createInstance(Transaction trunk, int classId, TType[] genericParameters) {
        throw new IllegalStateException();
    }

    protected abstract String getObjectFabricVersion();

    protected static final void register(ObjectModel model) {
        if (!CompileTimeSettings.OBJECTFABRIC_VERSION.equals(model.getObjectFabricVersion()))
            throw new IllegalArgumentException(Strings.WRONG_OBJECTFABRIC_VERSION);

        putObjectWithUIDIfAbsent(model.getSharedVersion_objectfabric().getUID(), model.getSharedVersion_objectfabric());
    }

    protected static class Version extends TObject.Version {

        public Version(TObject.Version shared) {
            super(shared);
        }

        @Override
        public int getClassId() {
            return DefaultObjectModel.COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID;
        }

        @Override
        public ObjectModel getObjectModel() {
            return DefaultObjectModelBase.getInstance();
        }

        @Override
        public boolean isImmutable() {
            return true;
        }
    }
}
