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

import cli.ObjectFabric.DoubleConverter;
import cli.ObjectFabric.FloatConverter;
import cli.System.Array;
import cli.System.Environment;
import cli.System.Guid;
import cli.System.Type;
import cli.System.IO.Path;

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

    final int value() {
        return CLR;
    }

    abstract URI resolve(String uri, URIResolver resolver);

    abstract ObjectModel clrDefaultObjectModel();

    static ObjectModel defaultObjectModel() {
        return _instance.clrDefaultObjectModel();
    }

    abstract TType newCLRTType(ObjectModel model, int classId, TType[] genericParameters);

    static TType newTType(ObjectModel model, int classId) {
        return newTType(model, classId, (TType[]) null);
    }

    static TType newTType(ObjectModel model, int classId, TType... genericParameters) {
        return _instance.newCLRTType(model, classId, genericParameters);
    }

    abstract TType[] newCLRTTypeArray(int length);

    static TType[] newTTypeArray(int length) {
        return _instance.newCLRTTypeArray(length);
    }

    //

    final String lineSeparator() {
        return Environment.get_NewLine();
    }

    final char fileSeparator() {
        return Path.PathSeparator;
    }

    final String[] split(String value, char... chars) {
        return ((cli.System.String) (Object) value).Split(chars);
    }

    static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        Array.Copy((Array) src, srcPos, (Array) dest, destPos, length);
    }

    final Object[] copyWithTypedResize(Object[] source, int length, Object[] target) {
        if (length <= ((Array) (Object) target).get_Length()) {
            Array.Copy((Array) (Object) source, 0, (Array) (Object) target, 0, length);
            return target;
        }

        Array temp = Array.CreateInstance(getClass(target).GetElementType(), length);
        Array.Copy((Array) (Object) source, temp, length);
        return (Object[]) (Object) temp;
    }

    final Object[] clone(Object[] array) {
        return (Object[]) ((cli.System.Array) (Object) array).Clone();
    }

    final long[] clone(long[] array) {
        return (long[]) ((cli.System.Array) (Object) array).Clone();
    }

    abstract Workspace newCustomWorkspace(CustomLocation location);

    final Buff newBuff(int capacity, boolean recycle) {
        return new CLRBuff(capacity, recycle);
    }

    abstract URI newURI(Origin origin, String path);

    final Object getReferenceQueue() {
        return null;
    }

    //

    abstract boolean randomBoolean();

    abstract int randomInt();

    abstract int randomInt(int limit);

    abstract double randomDouble();

    //

    @SuppressWarnings("unused")
    final void initializeUIDGenerator(byte[] uid) {
        if (Debug.ENABLED)
            Debug.assertion(uid.length == UID.LENGTH);

        // Ignored, used on GWT
    }

    final byte[] newUID() {
        byte[] bytes = Guid.NewGuid().ToByteArray();

        if (bytes.length != UID.LENGTH)
            throw new IllegalStateException();

        return bytes;
    }

    //

    static int floatToInt(float value) {
        return FloatConverter.ToInt(value);
    }

    static float intToFloat(int value) {
        return FloatConverter.ToFloat(value);
    }

    static String floatToString(float value) {
        return FloatConverter.ToString(value);
    }

    static float stringToFloat(String value) {
        return FloatConverter.ToFloat(value);
    }

    //

    static long doubleToLong(double value) {
        return DoubleConverter.ToLong(value);
    }

    static double longToDouble(long value) {
        return DoubleConverter.ToDouble(value);
    }

    static String doubleToString(double value) {
        return DoubleConverter.ToString(value);
    }

    static double stringToDouble(String value) {
        return DoubleConverter.ToDouble(value);
    }

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

    final String simpleName(Type c) {
        return c.get_Name();
    }

    final boolean isInstance(Type c, Object o) {
        return c.IsInstanceOfType(o);
    }

    final Type enclosingClass(Type c) {
        return c.get_DeclaringType();
    }

    final String name(Type c) {
        return c.get_Namespace() + '.' + get().simpleName(c);
    }

    final String defaultToString(Object o) {
        // TODO CLR style
        return name(getClass(o)) + "@" + o.hashCode();
    }

    final String className(Object o) {
        return name(getClass(o));
    }

    final Type superclass(Type type) {
        return type.get_BaseType();
    }

    abstract Type getClass(Object o);

    abstract String simpleClassName(Object o);

    abstract Type objectClass();

    abstract Type objectArrayClass();

    abstract Type stringClass();

    abstract Type voidClass();

    abstract Type tObjectClass();

    abstract Type tGeneratedClass();

    abstract Type tMapClass();

    abstract Type tKeyedClass();

    abstract Type transactionBaseClass();

    abstract Type tKeyedVersionClass();

    abstract Type byteArrayClass();

    /*
     * Generator
     */

    abstract void writeFile(String path, char[] text, int length);

    abstract void clearFolder(String folder);

    abstract boolean fileExists(String file);

    abstract void mkdir(String folder);

    @SuppressWarnings("unused")
    final String toXML(Object model, String schema) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    final Object fromXMLFile(String file) {
        throw new UnsupportedOperationException();
    }

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
        return new Resource(workspace, new URI() {
        });
    }

    //

    abstract boolean shallowEquals(Object a, Object b, Type c, String... exceptions);

    abstract Object getCurrentStack();

    abstract void assertCurrentStack(Object previous);

    abstract Object getPrivateField(Object object, String name, Type c);

    abstract TType getTypeField(Type c);

    abstract void writeAndResetAtomicLongs(Object object, boolean write);
}
