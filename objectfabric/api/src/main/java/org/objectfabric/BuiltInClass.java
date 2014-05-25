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

final class BuiltInClass {

    /*
     * Int constants not accessible in enums (?!), needed for partial switch.
     */

    static final BuiltInClass TOBJECT;

    static final int TOBJECT_CLASS_ID = 0;

    static final BuiltInClass URI;

    static final int RESOURCE_CLASS_ID = 1;

    static final BuiltInClass TSET;

    static final int TSET_CLASS_ID = 2;

    static final BuiltInClass TMAP;

    static final int TMAP_CLASS_ID = 3;

    static final BuiltInClass TCOUNTER;

    static final int COUNTER_CLASS_ID = 4;

    //

    static final BuiltInClass[] ALL;

    static {
        List<BuiltInClass> all = new List<BuiltInClass>();
        all.add(TOBJECT = new BuiltInClass(TOBJECT_CLASS_ID, "org.objectfabric.TObject"));
        all.add(URI = new BuiltInClass(RESOURCE_CLASS_ID, "org.objectfabric.Resource"));
        all.add(TSET = new BuiltInClass(TSET_CLASS_ID, "org.objectfabric.TSet"));
        all.add(TMAP = new BuiltInClass(TMAP_CLASS_ID, "org.objectfabric.TMap"));
        all.add(TCOUNTER = new BuiltInClass(COUNTER_CLASS_ID, "org.objectfabric.Counter"));
        ALL = new BuiltInClass[all.size()];
        all.copyToFixed(ALL);
    }

    private final int _id;

    private final String _name;

    private BuiltInClass(int id, String name) {
        _id = id;
        _name = name;
    }

    final int id() {
        return _id;
    }

    final String name() {
        return _name;
    }

    static BuiltInClass parse(String name) {
        if ("set".equals(name))
            return BuiltInClass.TSET;

        if ("Set".equals(name))
            return BuiltInClass.TSET;

        //

        if ("map".equals(name))
            return BuiltInClass.TMAP;

        if ("Map".equals(name))
            return BuiltInClass.TMAP;

        if ("Dictionary".equals(name))
            return BuiltInClass.TMAP;

        if ("TDictionary".equals(name))
            return BuiltInClass.TMAP;

        //

        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i]._name.equals(name))
                return ALL[i];

            String[] split = Platform.get().split(ALL[i]._name, '.');

            if (split.length > 0 && split[split.length - 1].equals(name))
                return ALL[i];
        }

        return null;
    }
}
