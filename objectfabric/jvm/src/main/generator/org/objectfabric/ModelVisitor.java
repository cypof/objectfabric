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
 * Transformations of a model, e.g. to parse types after loading.
 */
class ModelVisitor {

    private ObjectModelDef _model;

    private PackageDef _packageDef;

    private ValueSetDef _valueSetDef;

    ModelVisitor() {
    }

    final ObjectModelDef getObjectModelDef() {
        return _model;
    }

    final PackageDef getPackageDef() {
        return _packageDef;
    }

    final ValueSetDef getValueSetDef() {
        return _valueSetDef;
    }

    void visit(ObjectModelDef def) {
        _model = def;

        for (int i = 0; i < def.allPackages().size(); i++)
            visit(def.allPackages().get(i));
    }

    void visit(PackageDef def) {
        _packageDef = def;

        for (int i = 0; i < _packageDef.Classes.size(); i++)
            visit(_packageDef.Classes.get(i));
    }

    void visit(ClassDef def) {
        _valueSetDef = def;
        _packageDef = def.getPackage();

        for (int j = 0; j < def.Fields.size(); j++)
            def.Fields.get(j).visit(this);

        for (int j = 0; j < def.Methods.size(); j++)
            visit(def.Methods.get(j));
    }

    void visit(MethodDef def) {
        _valueSetDef = def;

        for (int t = 0; t < def.Arguments.size(); t++)
            def.Arguments.get(t).visit(this);

        def.ReturnValue.visit(this);
    }

    /**
     * @param def
     */
    void visit(ValueDef def) {
    }

    /**
     * Sets types names before a model is serialized to XML.
     */
    static final class BeforeMarshall extends ModelVisitor {

        private final Target _target;

        BeforeMarshall(Target target) {
            _target = target;
        }

        @Override
        void visit(ValueDef def) {
            super.visit(def);

            def.Type = def.type().getFullName(_target, true, true);
        }

        @Override
        void visit(ClassDef def) {
            super.visit(def);

            def.Parent = def.parent() != null ? def.parent().fullName(Target.JAVA) : null;
        }
    }

    /**
     * Parse types after a model has been deserialized from XML.
     */
    static final class AfterUnmarshall extends ModelVisitor {

        private final ObjectModelDef _model;

        AfterUnmarshall(ObjectModelDef model) {
            _model = model;
        }

        @Override
        void visit(ValueDef def) {
            super.visit(def);

            Debug.assertAlways(def.type() == null || def instanceof ReturnValueDef);
            def.type(org.objectfabric.TypeDef.parse(def.Type, _model));

            if (!isJavaIdentifier(def.Name))
                throw new IllegalArgumentException("Illegal iddentifier: \"" + def.Name + "\".");

            if (def.type().isJavaEnum())
                getValueSetDef().registerEnum(def.type());
        }

        @Override
        void visit(ClassDef def) {
            super.visit(def);

            if (def.Parent != null)
                def.parent(TypeDef.parse(def.Parent, _model));
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
