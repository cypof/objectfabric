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

class FileWriterTArrayJS extends FileGenerator {

    private final Class<?> _type;

    public FileWriterTArrayJS(Generator generator, String packag, Class<?> type) {
        super(generator, packag, "JSArray" + FileWriterTArray.getName(type));

        _type = type;
    }

    @Override
    protected void body() {
        StringBuilder template = FileWriterTArray.getTemplate("../js/src/main/template/org/objectfabric/generated/JSArrayTemplate.java");
        Immutable immutable = Immutable.parse(_type.getName());
        Immutable nonBoxed = null;

        if (immutable != null) {
            nonBoxed = immutable;

            if (immutable.isPrimitive()) {
                nonBoxed = immutable.nonBoxed();
                Utils.replace(template, "float", nonBoxed.java());
                Utils.replace(template, "Float", _type.getSimpleName());
            } else {
                if (_type != byte[].class) {
                    Utils.replace(template, "float", _type.getName());
                    Utils.replace(template, "Float", _type.getSimpleName());
                } else {
                    Utils.replace(template, "float", "byte[]");
                    Utils.replace(template, "Float", "Binary");
                }
            }

            Utils.replace(template, "Template", FileWriterTArray.getName(_type));
        } else if (_type == TObject.class) {
            Utils.replace(template, "Template", "TObject");
            Utils.replace(template, "float", "org.objectfabric.TObject");
            Utils.replace(template, "Float", "TObject");
        } else if (_type == Object.class) {
            Utils.replace(template, "Template", "");
            Utils.replace(template, "float", "Object");
            Utils.replace(template, "Float", "");
        }
        
        if (_type == TObject.class || _type == Object.class) {
            Utils.replace(template, "return _internal.get(index);", "return org.objectfabric.JS.out(_internal.get(index));");
            Utils.replace(template, "_internal.set(index, value);", "_internal.set(index, org.objectfabric.JS.in(value));");
        }

        write(template);
    }
}
