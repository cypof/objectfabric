
package org.objectfabric;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

final class Uint8Array extends JavaScriptObject {

    protected Uint8Array() {
    }

    static native Uint8Array create(int capacity) /*-{
    return new Uint8Array(capacity);
    }-*/;

    static native Uint8Array create(JavaScriptObject buffer) /*-{
    return new Uint8Array(buffer);
    }-*/;

    static native Uint8Array create(ArrayBuffer buffer) /*-{
    return new Uint8Array(buffer);
    }-*/;

    native short get(int index) /*-{
    return this[index];
    }-*/;

    native int length() /*-{
    return this.length;
    }-*/;

    native void set(int index, int value) /*-{
    this[index] = value;
    }-*/;

    native Uint8Array subarray(int begin) /*-{
    return this.subarray(begin);
    }-*/;

    native Uint8Array subarray(int begin, int end) /*-{
    return this.subarray(begin, end);
    }-*/;

    native void set(Uint8Array array, int offset) /*-{
    this.set(array, offset);
    }-*/;
}
