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

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Groups objects by 256. Weak references them so they can be retrieved by id.
 */
@SuppressWarnings("serial")
final class Range extends AtomicReferenceArray<Object> {

    static final class Id {

        final Peer Peer;

        final long Value;

        Id(Peer peer, long value) {
            Peer = peer;
            Value = value;
        }

        @Override
        public int hashCode() {
            return Peer.hashCode() ^ (int) (Value ^ (Value >>> 32));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Id) {
                Id other = (Id) obj;
                return Peer == other.Peer && Value == other.Value;
            }

            return false;
        }

        @Override
        public String toString() {
            return Peer.toString() + "-" + Value;
        }
    }

    static final class Ref extends WeakReference<TObject> {

        Ref(TObject object) {
            super(object);
        }
    }

    static final int SHIFT = 8;
    
    static final int LENGTH = 256;

    private final Id _id;

    /*
     * TODO Compact/reuse ids.
     */

    Range(Workspace workspace, Id id) {
        super(LENGTH);

        if (workspace == null || id == null)
            throw new IllegalArgumentException();

        _id = id;
    }

    final Id id() {
        return _id;
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "null" })
    final TObject getOrCreateTObject(Resource resource, int id, ObjectModel model, int classId, TType[] genericParameters) {
        TObject object;
        TObject newObject = null;

        for (;;) {
            Object value = get(id);
            object = null;

            if (value instanceof Ref) {
                object = ((Ref) value).get();

                if (object != null && object.resource() == resource)
                    break;
            }

            if (value instanceof PlatformConcurrentMap) {
                PlatformConcurrentMap map = (PlatformConcurrentMap) value;
                object = (TObject) map.get(resource);

                if (object != null)
                    break;
            }

            if (newObject == null) {
                newObject = model.createInstance(resource, classId, genericParameters);
                newObject.range(this);
                newObject.id(id);
                newObject.setReferencedByURI();
            }

            if (value == null || value instanceof Ref) {
                Object update;

                if (object == null)
                    update = new Ref(newObject);
                else {
                    /*
                     * Should never get there, only for security purposes. If a corrupt or
                     * crafted message tries to access an object from outside current
                     * resource, create a separate instance. Can't just close connection
                     * as this request might be the legitimate one.
                     */
                    PlatformConcurrentMap map = new PlatformConcurrentMap();
                    map.put(object.resource(), object);
                    map.put(resource, newObject);
                    update = map;
                }

                if (compareAndSet(id & 0xff, value, update)) {
                    object = newObject;
                    break;
                }
            } else {
                PlatformConcurrentMap map = (PlatformConcurrentMap) value;
                Object previous = map.putIfAbsent(resource, newObject);

                if (previous != null)
                    object = (TObject) previous;
                else
                    object = newObject;

                break;
            }
        }

        if (Debug.ENABLED) {
            Debug.assertion(object.resource() == resource);
            Debug.assertion(object.classId_() == classId);
            Debug.assertion(object.range() == this);
            Debug.assertion(object.id() == id);

            if (classId >= 0) {
                if (Platform.get().value() != Platform.GWT)
                    Debug.assertion(Platform.get().isInstance(model.getClass(classId, genericParameters), object));
            } else {
                Debug.assertion(model == Platform.get().defaultObjectModel());
                Helper.instance().disableEqualsOrHashCheck();
                Debug.assertion(Platform.get().defaultToString(object).contains("Array"));
                Helper.instance().enableEqualsOrHashCheck();
            }
        }

        return object;
    }
}
