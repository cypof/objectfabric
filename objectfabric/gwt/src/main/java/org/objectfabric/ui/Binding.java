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

package org.objectfabric.ui;

import org.objectfabric.TIndexed;

import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.UIObject;

/**
 * Binds a GWT UI control to an object, using ObjectFabric's change tracking and
 * notification system to make the binding automatic and bidirectional. Only TextBox is
 * currently implemented and the field the control is bound to must be a String.
 */
public abstract class Binding<T extends TIndexed, C extends UIObject> {

    private final C _control;

    private final int _field;

    private T _tObject;

    protected Binding(C control, int field) {
        _control = control;
        _field = field;
    }

    public C getControl() {
        return _control;
    }

    public int getField() {
        return _field;
    }

    public T getTObject() {
        return _tObject;
    }

    public void setTObject(T value) {
        _tObject = value;
    }

    public static <T extends TIndexed, C extends UIObject> Binding<T, TextBox> bind(C control, int field) {
        if (control instanceof TextBox)
            return new BindingTextBox<T>((TextBox) control, field);

        throw new IllegalArgumentException("Unsupported " + control.getClass().getName());
    }

    public abstract void dispose();
}