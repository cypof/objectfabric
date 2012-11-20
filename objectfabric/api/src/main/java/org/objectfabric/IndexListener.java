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

/**
 * Called when a field changes on a transactional object or array.
 */
public interface IndexListener {

    /**
     * @param field
     *            Index of the field which has changed. For an indexed collection like
     *            TArray, it is the index of the element. For a generated object, you can
     *            used the generated constants to find out which field changed. E.g.
     *            <code>if (index == MyClass.MY_FIELD_INDEX) { ... }</code>
     */
    void onSet(int index);
}
