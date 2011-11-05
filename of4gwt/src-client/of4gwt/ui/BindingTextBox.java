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

import of4gwt.FieldListener;
import of4gwt.TArray;
import of4gwt.TGeneratedFields;
import of4gwt.TIndexed;
import of4gwt.TList;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.TextBox;

final class BindingTextBox<T extends TIndexed> extends Binding<T, TextBox> {

    private FieldListener _fieldListener;

    private HandlerRegistration _changeHandler;

    private HandlerRegistration _keyPressHandler;

    public BindingTextBox(TextBox control, int fieldIndex) {
        super(control, fieldIndex);

        /*
         * If a transaction the object, update the control.
         */
        _fieldListener = new FieldListener() {

            public void onFieldChanged(int i) {
                if (i == getFieldIndex())
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
            getTObject().removeListener(_fieldListener);

        super.setTObject(value);

        if (getTObject() != null) {
            getTObject().addListener(_fieldListener);
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
        getControl().setText((String) get(getTObject(), getFieldIndex()));
    }

    private void onTextBoxChanged() {
        if (!getControl().getText().equals(get(getTObject(), getFieldIndex())))
            set(getTObject(), getFieldIndex(), getControl().getText());
    }

    private static final Object get(TIndexed object, int index) {
        if (object instanceof TGeneratedFields)
            return ((TGeneratedFields) object).getField(index);

        if (object instanceof TArray)
            return ((TArray) object).get(index);

        if (object instanceof TList)
            return ((TList) object).get(index);

        throw new IllegalArgumentException("Unsupported " + object.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static final void set(TIndexed object, int index, Object value) {
        if (object instanceof TGeneratedFields)
            ((TGeneratedFields) object).setField(index, value);
        else if (object instanceof TArray)
            ((TArray) object).set(index, value);
        else if (object instanceof TList)
            ((TList) object).set(index, value);
        else
            throw new IllegalArgumentException("Unsupported " + object.getClass().getName());
    }
}
