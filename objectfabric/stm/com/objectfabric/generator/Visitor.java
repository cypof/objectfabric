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

package com.objectfabric.generator;

import com.objectfabric.misc.Debug;

/**
 * Transformations of a model, e.g. to parse types after loading.
 */
class Visitor {

    private ObjectModelDef _model;

    private PackageDef _packageDef;

    private ValueSetDef _valueSetDef;

    public Visitor() {
    }

    public final ObjectModelDef getObjectModelDef() {
        return _model;
    }

    public final PackageDef getPackageDef() {
        return _packageDef;
    }

    public final ValueSetDef getValueSetDef() {
        return _valueSetDef;
    }

    public void visit(ObjectModelDef def) {
        _model = def;

        for (int i = 0; i < def.getAllPackages().size(); i++)
            visit(def.getAllPackages().get(i));
    }

    public void visit(PackageDef def) {
        _packageDef = def;

        for (int i = 0; i < _packageDef.Classes.size(); i++)
            visit(_packageDef.Classes.get(i));
    }

    public void visit(GeneratedClassDef def) {
        _valueSetDef = def;
        _packageDef = def.getPackage();

        for (int j = 0; j < def.Fields.size(); j++)
            def.Fields.get(j).visit(this);

        for (int j = 0; j < def.Methods.size(); j++)
            visit(def.Methods.get(j));
    }

    public void visit(MethodDef def) {
        _valueSetDef = def;

        for (int t = 0; t < def.Arguments.size(); t++)
            def.Arguments.get(t).visit(this);

        def.ReturnValue.visit(this);
    }

    public void visit(ValueDef def) {
    }

    /**
     * Sets types names before a model is serialized to XML.
     */
    public static final class BeforeMarshall extends Visitor {

        private final Target _target;

        public BeforeMarshall(Target target) {
            _target = target;
        }

        @Override
        public void visit(ValueDef def) {
            super.visit(def);

            def.Type = def.getType().getFullName(_target, true, true);
        }

        @Override
        public void visit(GeneratedClassDef def) {
            super.visit(def);

            def.Parent = def.getParent() != null ? def.getParent().getFullName(Target.JAVA) : null;
        }
    }

    /**
     * Parse types after a model has been deserialized from XML.
     */
    public static final class AfterUnmarshall extends Visitor {

        private final ObjectModelDef _model;

        public AfterUnmarshall(ObjectModelDef model) {
            _model = model;
        }

        @Override
        public void visit(ValueDef def) {
            super.visit(def);

            Debug.assertAlways(def.getType() == null || def instanceof ReturnValueDef);
            def.setType(com.objectfabric.generator.TypeDef.parse(def.Type, _model));

            if (!isJavaIdentifier(def.Name))
                throw new IllegalArgumentException("Illegal iddentifier: \"" + def.Name + "\".");

            if (def.getType().isJavaEnum())
                getValueSetDef().registerEnum(def.getType());
        }

        @Override
        public void visit(GeneratedClassDef def) {
            super.visit(def);

            if (def.Parent != null)
                def.setParent(TypeDef.parse(def.Parent, _model));
        }

        private static boolean isJavaIdentifier(String s) {
            if (s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0)))
                return false;

            for (int i = 1; i < s.length(); i++)
                if (!Character.isJavaIdentifierPart(s.charAt(i)))
                    return false;

            return true;
        }
    }
}
