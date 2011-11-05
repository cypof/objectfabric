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

package of4gwt.smartgwt;

import java.util.ArrayList;
import java.util.Iterator;

import of4gwt.ImmutableClass;
import of4gwt.TGeneratedFields;
import of4gwt.TSet;
import of4gwt.TType;
import of4gwt.misc.Utils;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.smartgwt.client.data.DSRequest;
import com.smartgwt.client.data.DSResponse;
import com.smartgwt.client.data.DataSource;
import com.smartgwt.client.data.DataSourceField;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.rpc.RPCResponse;
import com.smartgwt.client.types.DSDataFormat;
import com.smartgwt.client.types.DSProtocol;
import com.smartgwt.client.types.FieldType;

public abstract class OFDataSource<T extends TGeneratedFields> extends DataSource {

    private static final String PRIMARY_KEY = "__OF_PRIMARY_KEY__";

    private final TSet<T> _data;

    public OFDataSource(TSet<T> data) {
        setClientOnly(true);
        setDataProtocol(DSProtocol.CLIENTCUSTOM);
        setDataFormat(DSDataFormat.CUSTOM);
        // setCacheAllData(false);

        _data = data;

        // _data.addListener(new KeyListener() {
        //
        // @Override
        // public void onPut(Object object) {
        // ensureCreated();
        // add(object);
        // }
        //
        // @Override
        // public void onRemoved(Object object) {
        // remove(object);
        // }
        //
        // @Override
        // public void onCleared() {
        // }
        // });

        ensureCreated();

        // for (Object object : data)
        // add(object);
    }

    // Class

    protected DataSourceField createField(T object, int index) {
        String name = object.getFieldName(index);
        TType type = object.getFieldType(index);
        FieldType column;

        if (type.getImmutableClass() != null)
            column = map(type.getImmutableClass());
        else
            column = FieldType.ANY;

        return new DataSourceField(name, column, Utils.getWithFirstLetterUp(name));
    }

    protected void ensureCreated() {
        if (!isCreated()) {
            Iterator<T> iterator = _data.iterator();

            if (iterator.hasNext()) {
                DataSourceField primary = new DataSourceField(PRIMARY_KEY, FieldType.CUSTOM);
                primary.setPrimaryKey(true);
                primary.setHidden(true);
                addField(primary);

                T object = iterator.next();

                for (int i = 0; i < object.getFieldCount(); i++)
                    addField(createField(object, i));
            }
        }
    }

    // Instances

    protected abstract T createObject();

    protected void setField(T object, int index, Object value) {
        object.setField(index, value);
    }

    @Override
    protected Object transformRequest(DSRequest request) {
        DSResponse response = new DSResponse();
        response.setAttribute("clientContext", request.getAttributeAsObject("clientContext"));
        response.setStatus(RPCResponse.STATUS_SUCCESS);

        switch (request.getOperationType()) {
            case FETCH: {
                ArrayList<Record> list = new ArrayList<Record>();

                for (T object : _data) {
                    Record record = new Record();
                    record.setAttribute(PRIMARY_KEY, object);

                    for (int i = 0; i < object.getFieldCount(); i++) {
                        String name = object.getFieldName(i);
                        Object value = object.getField(i);
                        record.setAttribute(name, value);
                    }

                    list.add(record);
                }

                response.setData(list.toArray(new Record[list.size()]));
                scheduleResponse(request.getRequestId(), response);
                break;
            }
            case ADD: {
                Record record = Record.getOrCreateRef(request.getData());
                T object = createObject();
                record.setAttribute(PRIMARY_KEY, object);

                for (int i = 0; i < object.getFieldCount(); i++) {
                    String name = object.getFieldName(i);
                    Object value = record.getAttributeAsObject(name);
                    setField(object, i, value);
                }

                _data.add(object);
                scheduleResponse(request.getRequestId(), response);
                break;
            }
            case UPDATE: {
                Record record = Record.getOrCreateRef(request.getData());

                @SuppressWarnings("unchecked")
                T object = (T) record.getAttributeAsObject(PRIMARY_KEY);

                for (int i = 0; i < object.getFieldCount(); i++) {
                    String name = object.getFieldName(i);
                    Object value = record.getAttributeAsObject(name);
                    setField(object, i, value);
                }

                scheduleResponse(request.getRequestId(), response);
                break;
            }
            case REMOVE: {
                Record record = Record.getOrCreateRef(request.getData());

                @SuppressWarnings("unchecked")
                T object = (T) record.getAttributeAsObject(PRIMARY_KEY);
                _data.remove(object);
                scheduleResponse(request.getRequestId(), response);
                break;
            }
            default:
                // result = super.transformRequest(request);
                break;
        }

        return request.getData();
    }

    /**
     * Responses must be delayed, as they would be if data was retrieved from server,
     * otherwise grids do not seem to work. So process responses as ScheduledCommand.
     */
    private void scheduleResponse(final String requestId, final DSResponse response) {
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {

            @Override
            public void execute() {
                processResponse(requestId, response);
            }
        });
    }

    private static final FieldType map(ImmutableClass c) {
        switch (c.ordinal()) {
            case ImmutableClass.BOOLEAN_INDEX:
                return FieldType.BOOLEAN;
            case ImmutableClass.BOOLEAN_BOXED_INDEX:
                return FieldType.BOOLEAN;
            case ImmutableClass.BYTE_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.BYTE_BOXED_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.CHARACTER_INDEX:
                return FieldType.TEXT;
            case ImmutableClass.CHARACTER_BOXED_INDEX:
                return FieldType.TEXT;
            case ImmutableClass.SHORT_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.SHORT_BOXED_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.INTEGER_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.INTEGER_BOXED_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.LONG_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.LONG_BOXED_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.FLOAT_INDEX:
                return FieldType.FLOAT;
            case ImmutableClass.FLOAT_BOXED_INDEX:
                return FieldType.FLOAT;
            case ImmutableClass.DOUBLE_INDEX:
                return FieldType.FLOAT;
            case ImmutableClass.DOUBLE_BOXED_INDEX:
                return FieldType.FLOAT;
            case ImmutableClass.STRING_INDEX:
                return FieldType.TEXT;
            case ImmutableClass.DATE_INDEX:
                return FieldType.DATETIME;
            case ImmutableClass.BIG_INTEGER_INDEX:
                return FieldType.INTEGER;
            case ImmutableClass.DECIMAL_INDEX:
                return FieldType.FLOAT;
            case ImmutableClass.BINARY_INDEX:
                return FieldType.BINARY;
            default:
                return FieldType.TEXT;
        }
    }
}
