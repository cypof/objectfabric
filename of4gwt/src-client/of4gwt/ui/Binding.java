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

package of4gwt.ui;

import of4gwt.TIndexed;

import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.UIObject;

/**
 * Binds a GWT UI control to an object, using ObjectFabric's change tracking and
 * notification system to make the binding automatic and bidirectional. Only TextBox is
 * currently implemented and the field the control is bound to must be a String.
 */
public abstract class Binding<T extends TIndexed, C extends UIObject> {

    private final T _tObject;

    private final C _control;

    private final int _fieldIndex;

    protected Binding(T tObject, C control, int fieldIndex) {
        _control = control;
        _tObject = tObject;
        _fieldIndex = fieldIndex;
    }

    public T getTObject() {
        return _tObject;
    }

    public C getControl() {
        return _control;
    }

    public int getFieldIndex() {
        return _fieldIndex;
    }

    public static <T extends TIndexed, C extends UIObject> Binding<T, TextBox> bind(T object, C control, int fieldIndex) {
        if (control instanceof TextBox)
            return new BindingTextBox<T>(object, (TextBox) control, fieldIndex);

        throw new IllegalArgumentException("Unsupported " + control.getClass().getName());
    }

    public abstract void dispose();
}