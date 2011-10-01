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

package part08.versioning;

import org.junit.Test;

import part08.versioning.generated.ObjectModel;
import part08.versioning.generated.SimpleClass;

import com.objectfabric.TObject;
import com.objectfabric.misc.PlatformAdapter;

/**
 * Shows how an application can load several versions of the same object model, for
 * versioning purposes. This is useful e.g. to read objects from a store, or communicate
 * with a process, which uses an older version of an object model.
 * <nl>
 * Loading two versions of an object model simplifies development of data transformation
 * tools when updating an application. It also enables always-on and large scale systems,
 * which must keep running while their data is progressively upgraded to a new model. Old
 * code and object models can be removed once the new version is validated and all data
 * has been converted.
 * <nl>
 * In practice, before generating a new version of an object model, copy the old generated
 * code to a new package, e.g. "generated.v1". Packages and class names are ignored by
 * ObjectFabric which relies only on UID and class id embedded in generated code.
 * <nl>
 * TODO: We are working on a tool to generate interfaces from an object model. Those
 * interfaces would implement the common subset between two versions of a model, and
 * simplify application code related to versioning.
 */
public class Main {

    public static void run() {
        /*
         * Register both the new and old renamed version of your object model.
         */
        ObjectModel.register();
        part08.versioning.generated.v1.ObjectModel.register();

        /*
         * Let's say your application needs to load an object from a store, or download it
         * from a remote machine which might be running an old version of your software.
         */
        TObject object = loadDataWithUnknownVersion();

        /*
         * If object turns out to be old version, either run old code, or convert it to
         * new version.
         */
        if (object instanceof part08.versioning.generated.v1.SimpleClass) {
            /*
             * Create a new version object.
             */
            SimpleClass newVersion = new SimpleClass();
            part08.versioning.generated.v1.SimpleClass oldVersion = (part08.versioning.generated.v1.SimpleClass) object;

            /*
             * Transform data from the old model (e.g. text) to the new one (int).
             */
            newVersion.setValue(Integer.parseInt(oldVersion.getText()));
        }

        /*
         * If data must be migrated to new store instead, unmodified objects also needs to
         * be copied, since an object cannot be part of two different stores. ObjectFabric
         * offers methods to access fields using an index without resorting to reflection
         * (For GWT and .NET support).
         */
        SimpleClass unmodifiedObjectFromStoreA = new SimpleClass();
        SimpleClass newInstanceToWriteToStoreB = new SimpleClass();

        for (int i = 0; i < SimpleClass.FIELD_COUNT; i++) {
            Object field = unmodifiedObjectFromStoreA.getField(i);
            newInstanceToWriteToStoreB.setField(i, field);
        }
    }

    private static TObject loadDataWithUnknownVersion() {
        part08.versioning.generated.v1.SimpleClass object = new part08.versioning.generated.v1.SimpleClass();
        object.setText("42");
        return object;
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    @Test
    public void asTest() {
        run();
        PlatformAdapter.reset();
    }
}
