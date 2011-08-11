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

package of4gwt;

import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;

final class BuiltInClass {

    // Generated

    public static final BuiltInClass CONNECTION;

    public static final BuiltInClass INDEX;

    public static final BuiltInClass LAZY_MAP;

    public static final BuiltInClass SESSION;

    public static final BuiltInClass SITE;

    public static final BuiltInClass TRANSACTION;

    // Manual

    public static final BuiltInClass TOBJECT;

    public static final BuiltInClass OBJECT_MODEL;

    public static final BuiltInClass TLIST;

    public static final BuiltInClass TMAP;

    public static final BuiltInClass TSET;

    //

    public static final BuiltInClass[] ALL;

    static {
        List<BuiltInClass> all = new List<BuiltInClass>();

        all.add(CONNECTION = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_CONNECTION_CLASS_ID, "of4gwt.Connection"));
        all.add(INDEX = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_INDEX_CLASS_ID, "of4gwt.Index"));
        all.add(LAZY_MAP = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID, "of4gwt.LazyMap"));
        all.add(SESSION = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_SESSION_CLASS_ID, "of4gwt.Session"));
        all.add(SITE = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_SITE_CLASS_ID, "of4gwt.Site"));
        all.add(TRANSACTION = new BuiltInClass(DefaultObjectModelBase.COM_OBJECTFABRIC_TRANSACTION_CLASS_ID, "of4gwt.Transaction"));

        for (int i = 0; i < DefaultObjectModelBase.METHOD_COUNT; i++)
            all.add(new BuiltInClass(DefaultObjectModelBase.CLASS_COUNT + i, "of4gwt.DefaultObjectModelBase.Method" + i));

        all.add(TOBJECT = new BuiltInClass(DefaultObjectModel.COM_OBJECTFABRIC_TOBJECT_CLASS_ID, "of4gwt.TObject.UserTObject"));
        all.add(OBJECT_MODEL = new BuiltInClass(DefaultObjectModel.COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID, "of4gwt.ObjectModel"));
        all.add(TLIST = new BuiltInClass(DefaultObjectModel.COM_OBJECTFABRIC_TLIST_CLASS_ID, "of4gwt.TList"));
        all.add(TMAP = new BuiltInClass(DefaultObjectModel.COM_OBJECTFABRIC_TMAP_CLASS_ID, "of4gwt.TMap"));
        all.add(TSET = new BuiltInClass(DefaultObjectModel.COM_OBJECTFABRIC_TSET_CLASS_ID, "of4gwt.TSet"));
        ALL = new BuiltInClass[all.size()];
        all.copyToFixed(ALL);

        if (Debug.ENABLED && PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_DOT_NET) {
            for (int i = 0; i < all.size(); i++) {
                Debug.assertion(all.get(i).getId() == i);
                String name = PlatformClass.getName(DefaultObjectModel.getInstance().getClass(i, null));
                Debug.assertion(name.replace('$', '.').equals(all.get(i).getName()));
            }
        }
    }

    private final int _id;

    private final String _name;

    private BuiltInClass(int id, String name) {
        _id = id;
        _name = name;
    }

    public int getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public static BuiltInClass parse(String name) {
        if ("map".equals(name))
            return BuiltInClass.TMAP;

        if ("Map".equals(name))
            return BuiltInClass.TMAP;

        if ("Dictionary".equals(name))
            return BuiltInClass.TMAP;

        if ("TDictionary".equals(name))
            return BuiltInClass.TMAP;

        //

        if ("set".equals(name))
            return BuiltInClass.TSET;

        if ("Set".equals(name))
            return BuiltInClass.TSET;

        //

        if ("list".equals(name))
            return BuiltInClass.TLIST;

        if ("List".equals(name))
            return BuiltInClass.TLIST;

        //

        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i]._name.equals(name))
                return ALL[i];

            String[] split = PlatformAdapter.split(ALL[i]._name, '.');

            if (split.length > 0 && split[split.length - 1].equals(name))
                return ALL[i];
        }

        return null;
    }
}
