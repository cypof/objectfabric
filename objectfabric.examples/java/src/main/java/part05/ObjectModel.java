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

package part05;

import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.objectfabric.Counter;
import org.objectfabric.Generator;
import org.objectfabric.Immutable;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.NettyURIHandler;
import org.objectfabric.Resource;
import org.objectfabric.TArrayInteger;
import org.objectfabric.TMap;
import org.objectfabric.TObject;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

import part05.generated.Car;
import part05.generated.ElectricCar;
import part05.generated.MyObjectModel;

/**
 * ObjectFabric supports basic types like String, Integer, Date or byte[] (C.f.
 * {@link Immutable}), collections like {@link TSet}, arrays (TArray*), and
 * {@link Counter}. Objects have referential identity and can reference other objects from
 * the same {@link Resource} in arbitrary graphs. <br>
 * <br>
 * In addition to built-in types, objects can be created through code generation.
 * Generated code includes accessors for each field, change tracking & transaction
 * support, and type-specialized serializers which validate data and offer very high
 * performance. ObjectFabric's code {@link Generator} can be driven programmatically or by
 * importing existing models (C.f. /generator/Main.java). XML models are currently
 * supported, with SQL and runtime reflection in progress.
 */
@SuppressWarnings("unchecked")
public class ObjectModel {

    public static void main(String[] args) throws Exception {
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(new NettyURIHandler());
        String uri = "ws://localhost:8888";

        /*
         * Immutable types. (C.f. Immutable for full list)
         */

        String string = (String) workspace.resolve(uri + "/string").get();
        Assert.assertEquals("{\"key\": \"value\"}", string);

        int i = (Integer) workspace.resolve(uri + "/int").get();
        Assert.assertEquals(1, i);

        byte[] binary = (byte[]) workspace.resolve(uri + "/bin").get();
        Assert.assertEquals(0, binary[0]);
        Assert.assertEquals(1, binary[1]);

        /*
         * Collections & arrays.
         */

        Set<String> set = (TSet) workspace.resolve(uri + "/set").get();
        Assert.assertTrue(set.contains("blah"));

        Map<String, Integer> map = (TMap) workspace.resolve(uri + "/map").get();
        Assert.assertEquals(42, (int) map.get("example key"));

        TArrayInteger ints = (TArrayInteger) workspace.resolve(uri + "/arrayOfInt").get();
        Assert.assertEquals(10, ints.length());
        Assert.assertEquals(0, ints.get(0));
        Assert.assertEquals(1, ints.get(5));
        Assert.assertEquals(0, ints.get(8));

        /*
         * Counters.
         */

        Counter counter = (Counter) workspace.resolve(uri + "/counter").get();
        Assert.assertEquals(1, counter.get());

        /*
         * Custom objects. Generated object model must be registered to allow object
         * deserialization.
         */

        MyObjectModel.register();

        Car car = (Car) workspace.resolve(uri + "/car").get();

        // Object fields can be of any of the supported types
        Assert.assertEquals("DeLorean", car.brand());
        Assert.assertEquals("Joe", car.driver().name());
        Assert.assertEquals(1, car.settings().size());

        // Generator supports inheritance
        ElectricCar child = car.child();
        Assert.assertEquals("Tesla", child.brand());

        /*
         * Custom objects versioning. For versioning purposes an application can load
         * multiple versions of the same object model. Before generating a new version of
         * an object model, the old model can be copied by renaming its packages, e.g. to
         * "generated.v1". Packages and class names are ignored by ObjectFabric which
         * relies only on the UID and class ids embedded in generated code.
         */

        // Register the old renamed object model.
        part05.generated.v1.ObjectModel.register();

        /*
         * Let's say an application needs to load objects from a store, or from a remote
         * machine which might be running an older software version.
         */
        TObject object = loadDataOfUnknownVersion(workspace);

        /*
         * If object turns out to be of the old version, either run old code, or convert
         * it to new version.
         */
        if (object instanceof part05.generated.v1.Car) {
            part05.generated.v1.Car oldCar = (part05.generated.v1.Car) object;

            /*
             * Transform data from the old model to the new one.
             */
            Car newCar = new Car(oldCar.resource(), oldCar.brand());
            newCar.field(Integer.parseInt(oldCar.field()));
        }

        System.out.println("Done!");
        workspace.close();
    }

    private static TObject loadDataOfUnknownVersion(Workspace workspace) {
        Resource local = workspace.resolve("");
        part05.generated.v1.Car object = new part05.generated.v1.Car(local, "test");
        object.field("42");
        return object;
    }
}
