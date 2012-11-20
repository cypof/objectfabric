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

import java.util.ArrayList;

/**
 * Allows to test if a function override calls it's base. Add before and after each call
 * to the function to test calls to 'add' and 'end', and 'set' in the base declaration. If
 * the base is not called, 'end' will fail.
 */
final class OverrideAssert {

    private OverrideAssert() {
    }

    private static ArrayList<Boolean> bools() {
        ThreadAssert context = ThreadAssert.getOrCreateCurrent();
        return context.getOverrideAssertBools();
    }

    private static ArrayList<Object> keys() {
        ThreadAssert context = ThreadAssert.getOrCreateCurrent();
        return context.getOverrideAssertKeys();
    }

    public static void add(Object key) {
        if (Debug.ENABLED) {
            bools().add(false);
            keys().add(key);

            Debug.assertion(bools().size() == keys().size());
        }
    }

    public static void set(Object key) {
        if (Debug.ENABLED) {
            ArrayList<Boolean> bools = bools();
            ArrayList<Object> keys = keys();

            Debug.assertion(!bools.remove(bools.size() - 1).booleanValue());
            bools.add(new Boolean(true));
            Debug.assertion(key == keys.get(keys.size() - 1));

            Debug.assertion(bools.size() == keys.size());
        }
    }

    public static void end(Object key) {
        if (Debug.ENABLED) {
            ArrayList<Boolean> bools = bools();
            ArrayList<Object> keys = keys();

            Debug.assertion(bools.remove(bools.size() - 1).booleanValue());
            Debug.assertion(key == keys.remove(keys.size() - 1));

            Debug.assertion(bools.size() == keys.size());
        }
    }
}
