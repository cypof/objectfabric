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

import org.objectfabric.IndexListener;
import org.objectfabric.TArray;
import org.objectfabric.TGenerated;
import org.objectfabric.TIndexed;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.TextBox;

final class BindingTextBox<T extends TIndexed> extends Binding<T, TextBox> {

    private IndexListener _listener;

    private HandlerRegistration _changeHandler;

    private HandlerRegistration _keyPressHandler;

    public BindingTextBox(TextBox control, int fieldIndex) {
        super(control, fieldIndex);

        /*
         * If a transaction the object, update the control.
         */
        _listener = new IndexListener() {

            public void onSet(int i) {
                if (i == getField())
                    updateTextBox();
            }
        };

        _changeHandler = getControl().addChangeHandler(new ChangeHandler() {

            public void onChange(ChangeEvent event) {
                onTextBoxChanged();
            }
        });

        _keyPressHandler = getControl().addKeyPressHandler(new KeyPressHandler() {

            public void onKeyPress(KeyPressEvent event) {
                onTextBoxChanged();
            }
        });
    }

    @Override
    public void setTObject(T value) {
        if (getTObject() != null)
            getTObject().removeListener(_listener);

        super.setTObject(value);

        if (getTObject() != null) {
            getTObject().addListener(_listener);
            updateTextBox();
        }
    }

    @Override
    public void dispose() {
        _changeHandler.removeHandler();
        _keyPressHandler.removeHandler();

        setTObject(null);
    }

    private void updateTextBox() {
        getControl().setText((String) get(getTObject(), getField()));
    }

    private void onTextBoxChanged() {
        if (!getControl().getText().equals(get(getTObject(), getField())))
            set(getTObject(), getField(), getControl().getText());
    }

    private static Object get(TIndexed object, int index) {
        if (object instanceof TGenerated)
            return ((TGenerated) object).getField(index);

        if (object instanceof TArray)
            return ((TArray) object).get(index);

        throw new IllegalArgumentException("Unsupported " + object.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static void set(TIndexed object, int index, Object value) {
        if (object instanceof TGenerated)
            ((TGenerated) object).setField(index, value);
        else if (object instanceof TArray)
            ((TArray) object).set(index, value);
        else
            throw new IllegalArgumentException("Unsupported " + object.getClass().getName());
    }
}
