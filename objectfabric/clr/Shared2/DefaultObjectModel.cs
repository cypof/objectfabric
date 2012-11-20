///
/// This file is part of ObjectFabric (http://objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// Copyright ObjectFabric Inc.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
///

using System;

namespace ObjectFabric
{
    class DefaultObjectModel : ObjectModel
    {
        internal static readonly DefaultObjectModel Instance = new DefaultObjectModel();

        internal DefaultObjectModel()
        {
        }

        protected internal override byte[] uid_()
        {
            throw new Exception();
        }

        protected internal override string objectFabricVersion()
        {
            throw new Exception();
        }

        protected internal override Type getClass(int classId, org.objectfabric.TType[] genericParameters)
        {
            if (classId < 0)
            {
                if (genericParameters == null)
                    return typeof(TArray<object>);

                if (org.objectfabric.Debug.ENABLED)
                    org.objectfabric.Debug.assertion(genericParameters.Length == 1);

                if (genericParameters[0].getObjectModel() == null)
                {
                    switch (genericParameters[0].getClassId())
                    {
                        case org.objectfabric.Immutable.BOOLEAN_INDEX:
                            return typeof(TArray<bool>);
                        case org.objectfabric.Immutable.BYTE_INDEX:
                            return typeof(TArray<byte>);
                        case org.objectfabric.Immutable.CHARACTER_INDEX:
                            return typeof(TArray<char>);
                        case org.objectfabric.Immutable.SHORT_INDEX:
                            return typeof(TArray<short>);
                        case org.objectfabric.Immutable.INTEGER_INDEX:
                            return typeof(TArray<int>);
                        case org.objectfabric.Immutable.LONG_INDEX:
                            return typeof(TArray<long>);
                        case org.objectfabric.Immutable.FLOAT_INDEX:
                            return typeof(TArray<float>);
                        case org.objectfabric.Immutable.DOUBLE_INDEX:
                            return typeof(TArray<double>);
                        case org.objectfabric.Immutable.STRING_INDEX:
                            return typeof(TArray<string>);
                        case org.objectfabric.Immutable.DATE_INDEX:
                            return typeof(TArray<DateTime>);
                        case org.objectfabric.Immutable.BIG_INTEGER_INDEX:
                            return typeof(TArray<System.Numerics.BigInteger>);
                        case org.objectfabric.Immutable.DECIMAL_INDEX:
                            return typeof(TArray<decimal?>);
                        case org.objectfabric.Immutable.BINARY_INDEX:
                            return typeof(TArray<byte[]>);
                        default:
                            throw new java.lang.IllegalStateException(org.objectfabric.Strings.INVALID_ELEMENT_TYPE + genericParameters[0].getClassId());
                    }
                }

                // TODO: special case for TObject?

                Type T = ((TType) genericParameters[0]).ToType();
                return typeof(TArray<>).MakeGenericType(T);
            }

            switch (classId)
            {
                case org.objectfabric.BuiltInClass.TOBJECT_CLASS_ID:
                    return typeof(CLRTObject);
                case org.objectfabric.BuiltInClass.RESOURCE_CLASS_ID:
                    return typeof(Resource);
                case org.objectfabric.BuiltInClass.TSET_CLASS_ID:
                    if (genericParameters != null)
                    {
                        if (org.objectfabric.Debug.ENABLED)
                            org.objectfabric.Debug.assertion(genericParameters.Length == 1);

                        Type T = ((TType) genericParameters[0]).ToType();
                        return typeof(TSet<>).MakeGenericType(T);
                    }

                    return typeof(TSet<object>);
                case org.objectfabric.BuiltInClass.TMAP_CLASS_ID:
                    if (genericParameters != null)
                    {
                        if (org.objectfabric.Debug.ENABLED)
                            org.objectfabric.Debug.assertion(genericParameters.Length == 2);

                        Type K = ((TType) genericParameters[0]).ToType();
                        Type V = ((TType) genericParameters[1]).ToType();
                        return typeof(TDictionary<,>).MakeGenericType(K, V);
                    }

                    return typeof(TDictionary<object, object>);
                case org.objectfabric.BuiltInClass.COUNTER_CLASS_ID:
                    return typeof(Counter);
            }

            return base.getClass(classId, genericParameters);
        }

        protected internal override org.objectfabric.TObject createInstance(org.objectfabric.Resource resource, int classId, org.objectfabric.TType[] genericParameters)
        {
            org.objectfabric.TObject result = createInstanceImpl(resource, classId, genericParameters);

            if (org.objectfabric.Debug.ENABLED)
                org.objectfabric.Debug.assertion(result is TObject);

            return result;
        }

        private org.objectfabric.TObject createInstanceImpl(org.objectfabric.Resource resource, int classId, org.objectfabric.TType[] genericParameters)
        {
            if (classId < 0)
            {
                if (genericParameters == null)
                    return new TArray<object>((Resource) resource, -classId - 1);

                if (org.objectfabric.Debug.ENABLED)
                    org.objectfabric.Debug.assertion(genericParameters.Length == 1);

                if (genericParameters[0].getObjectModel() == null)
                {
                    switch (genericParameters[0].getClassId())
                    {
                        case org.objectfabric.Immutable.BOOLEAN_INDEX:
                            return new TArray<bool>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.BYTE_INDEX:
                            return new TArray<byte>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.CHARACTER_INDEX:
                            return new TArray<char>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.SHORT_INDEX:
                            return new TArray<short>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.INTEGER_INDEX:
                            return new TArray<int>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.LONG_INDEX:
                            return new TArray<long>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.FLOAT_INDEX:
                            return new TArray<float>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.DOUBLE_INDEX:
                            return new TArray<double>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.STRING_INDEX:
                            return new TArray<string>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.DATE_INDEX:
                            return new TArray<DateTime>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.BIG_INTEGER_INDEX:
                            return new TArray<System.Numerics.BigInteger>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.DECIMAL_INDEX:
                            return new TArray<decimal?>((Resource) resource, -classId - 1);
                        case org.objectfabric.Immutable.BINARY_INDEX:
                            return new TArray<byte[]>((Resource) resource, -classId - 1);
                        default:
                            throw new java.lang.IllegalStateException(org.objectfabric.Strings.INVALID_ELEMENT_TYPE + genericParameters[0].getClassId());
                    }
                }

                // TODO: special case for TObject?

                Type T = ((TType) genericParameters[0]).ToType();
                Type type = typeof(TArray<>).MakeGenericType(T);
                return (org.objectfabric.TObject) Activator.CreateInstance(type, resource, -classId - 1);
            }

            switch (classId)
            {
                case org.objectfabric.BuiltInClass.TOBJECT_CLASS_ID:
                    return new CLRTObject((Resource) resource);
                case org.objectfabric.BuiltInClass.RESOURCE_CLASS_ID:
                    return resource;
                case org.objectfabric.BuiltInClass.TSET_CLASS_ID:
                    if (genericParameters != null)
                    {
                        if (org.objectfabric.Debug.ENABLED)
                            org.objectfabric.Debug.assertion(genericParameters.Length == 1);

                        Type T = ((TType) genericParameters[0]).ToType();
                        Type type = typeof(TSet<>).MakeGenericType(T);
                        return (org.objectfabric.TObject) Activator.CreateInstance(type, resource);
                    }

                    return new TSet<object>((Resource) resource);
                case org.objectfabric.BuiltInClass.TMAP_CLASS_ID:
                    if (genericParameters != null)
                    {
                        if (org.objectfabric.Debug.ENABLED)
                            org.objectfabric.Debug.assertion(genericParameters.Length == 2);

                        Type K = ((TType) genericParameters[0]).ToType();
                        Type V = ((TType) genericParameters[1]).ToType();
                        Type type = typeof(TDictionary<,>).MakeGenericType(K, V);
                        return (org.objectfabric.TObject) Activator.CreateInstance(type, resource);
                    }

                    return new TDictionary<object, object>((Resource) resource);
                case org.objectfabric.BuiltInClass.COUNTER_CLASS_ID:
                    return new Counter((Resource) resource);
            }

            return base.createInstance(resource, classId, genericParameters);
        }

        class CLRTObject : org.objectfabric.TObject, TObject
        {
            public CLRTObject(Resource resource)
                : base(resource)
            {
            }

            public Resource Resource
            {
                get { return (Resource) resource(); }
            }

            public Workspace Workspace
            {
                get { return (Workspace) workspace(); }
            }
        }
    }
}