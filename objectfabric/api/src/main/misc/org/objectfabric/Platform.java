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

import org.objectfabric.Workspace.Granularity;

/**
 * Allows ports to platforms like GWT and .NET. For .NET, it makes it possible to remove
 * Java components like Reflection and Security from the ObjectFabric dll.
 */
@SuppressWarnings("rawtypes")
abstract class Platform {

    static final int JVM = 0, CLR = 1, GWT = 2;

    private static Platform _instance;

    static Platform get() {
        return _instance;
    }

    static void set(Platform value) {
        if (Debug.ENABLED)
            Debug.assertion(value != null);

        _instance = value;
    }

    abstract int value();

    //

    abstract URI resolve(String uri, URIResolver resolver);

    //

    ObjectModel defaultObjectModel() {
        return DefaultObjectModel.Instance;
    }

    //

    static TType newTType(ObjectModel model, int classId) {
        return newTType(model, classId, (TType[]) null);
    }

    static TType newTType(ObjectModel model, int classId, TType... genericParameters) {
        return new TType(model, classId, genericParameters);
    }

    static TType[] newTTypeArray(int length) {
        return new TType[length];
    }

    //

    abstract String lineSeparator();

    abstract char fileSeparator();

    abstract String[] split(String value, char... chars);

    static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    abstract Object[] copyWithTypedResize(Object[] source, int length, Object[] target);

    abstract Object[] clone(Object[] array);

    abstract long[] clone(long[] array);

    URI newURI(Origin origin, String path) {
        return new URI(origin, path);
    }

    abstract Workspace newCustomWorkspace(CustomLocation store);

    abstract Buff newBuff(int capacity, boolean recycle);

    abstract Object getReferenceQueue();

    //

    abstract boolean randomBoolean();

    abstract int randomInt();

    abstract int randomInt(int limit);

    abstract double randomDouble();

    //

    final byte[] newUID() {
        byte[] bytes = newUID_();

        if (Debug.ENABLED)
            Debug.assertion(bytes.length == UID.LENGTH);

        return bytes;
    }

    abstract byte[] newUID_();

    //

    abstract int floatToInt(float value);

    abstract float intToFloat(int value);

    abstract long doubleToLong(double value);

    abstract double longToDouble(long value);

    //

    abstract String formatLog(String message);

    abstract void logDefault(String message);

    abstract String getStackAsString(Throwable t);

    //

    abstract void sleep(long millis);

    abstract void assertLock(Object lock, boolean hold);

    abstract void execute(Runnable runnable);

    abstract void schedule(Runnable runnable, int ms);

    abstract long approxTimeMs();

    /*
     * Class
     */

    abstract String simpleName(Class c);

    abstract boolean isInstance(Class c, Object o);

    abstract Class enclosingClass(Class c);

    final Class getClass(Object o) {
        return o.getClass();
    }

    final String simpleClassName(Object o) {
        return simpleName(o.getClass());
    }

    final String name(Class c) {
        return c.getName();
    }

    final String defaultToString(Object o) {
        return o.getClass().getName() + "@" + Integer.toHexString(o.hashCode());
    }

    final Class superclass(Class c) {
        return c.getSuperclass();
    }

    final Class objectClass() {
        return Object.class;
    }

    final Class objectArrayClass() {
        return Object[].class;
    }

    final Class stringClass() {
        return String.class;
    }

    final Class voidClass() {
        return void.class;
    }

    final Class tObjectClass() {
        return TObject.class;
    }

    final Class tGeneratedClass() {
        return TGenerated.class;
    }

    final Class transactionBaseClass() {
        return TransactionBase.class;
    }

    final Class tKeyedVersionClass() {
        return TKeyedVersion.class;
    }

    /*
     * Generator
     */

    abstract String toXML(Object model, String schema);

    abstract Object fromXMLFile(String file);

    /*
     * Debug
     */

    abstract Workspace newTestWorkspace(Granularity granularity);

    static Workspace newTestWorkspace() {
        return get().newTestWorkspace(Granularity.COALESCE);
    }

    abstract Server newTestServer();

    abstract URIHandler newTestStore(String path);

    static Resource newTestResource(Workspace workspace) {
        return new Resource(workspace, new URI());
    }

    //

    abstract boolean shallowEquals(Object a, Object b, Class c, String... exceptions);

    abstract Object getCurrentStack();

    abstract void assertCurrentStack(Object previous);

    abstract Object getPrivateField(Object object, String name, Class c);

    abstract TType getTypeField(Class c);

    abstract void writeAndResetAtomicLongs(Object object, boolean write);
}
