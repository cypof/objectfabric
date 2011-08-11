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

package com.objectfabric;

/**
 * Indexing/Querying for stores. Private class as this is still work in progress.
 */
class Index extends IndexBase {

    public static abstract class Insert {

        public final TObject Object;

        private byte[] _data;

        private Insert _next;

        public Insert(TObject object) {
            Object = object;
        }

        public final Insert getNext() {
            return _next;
        }

        public final void setNext(Insert value) {
            _next = value;
        }

        public final byte[] getData() {
            return _data;
        }

        public final void setData(byte[] value) {
            _data = value;
        }

        public abstract void begin();

        public abstract void commit();

        public abstract void onSuccess();

        public abstract void onFailure(Throwable throwable);
    }

    protected Index(Transaction trunk) {
        super(new IndexBase.Version(null, FIELD_COUNT), trunk);
    }
}
