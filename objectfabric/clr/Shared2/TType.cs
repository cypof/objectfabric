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
using System.Reflection;

namespace ObjectFabric
{
    public sealed class TType : org.objectfabric.TType
    {
        static TType()
        {
            CLRPlatform.LoadClass();
        }

        internal TType(ObjectModel model, int classId, params TType[] genericParameters)
            : this((org.objectfabric.ObjectModel) model, classId, (org.objectfabric.TType[]) genericParameters)
        {
        }

        internal TType(org.objectfabric.ObjectModel model, int classId, params org.objectfabric.TType[] genericParameters)
            : base(model, classId, genericParameters)
        {
        }

        internal static TType From(Type type)
        {
            TType result = null;

            // C.f. TTypeGenerator

            if (type == typeof(bool))
                result = (TType) org.objectfabric.Immutable.BOOLEAN.type();
            else if (type == typeof(byte))
                result = (TType) org.objectfabric.Immutable.BYTE.type();
            else if (type == typeof(char))
                result = (TType) org.objectfabric.Immutable.CHARACTER.type();
            else if (type == typeof(short))
                result = (TType) org.objectfabric.Immutable.SHORT.type();
            else if (type == typeof(int))
                result = (TType) org.objectfabric.Immutable.INTEGER.type();
            else if (type == typeof(long))
                result = (TType) org.objectfabric.Immutable.LONG.type();
            else if (type == typeof(float))
                result = (TType) org.objectfabric.Immutable.FLOAT.type();
            else if (type == typeof(double))
                result = (TType) org.objectfabric.Immutable.DOUBLE.type();
            else if (type == typeof(string))
                result = (TType) org.objectfabric.Immutable.STRING.type();
            else if (type == typeof(System.DateTime?))
                result = (TType) org.objectfabric.Immutable.DATE.type();
            else if (type == typeof(System.Numerics.BigInteger?))
                result = (TType) org.objectfabric.Immutable.BIG_INTEGER.type();
            else if (type == typeof(decimal?))
                result = (TType) org.objectfabric.Immutable.DECIMAL.type();
            else if (type == typeof(byte[]))
                result = (TType) org.objectfabric.Immutable.BINARY.type();
            else
                result = GetFromField(type);

            return result;
        }

        internal static TType GetFromField(Type type)
        {
            while (type != null)
            {
                foreach (FieldInfo field in type.GetRuntimeFields())
                    if (field.IsStatic && field.Name == "TYPE")
                        if (field.FieldType == typeof(TType) || field.FieldType == typeof(org.objectfabric.TType))
                            return (TType) field.GetValue(null);

                type = type.GetTypeInfo().BaseType;
            }

            return null;
        }

        internal Type ToType()
        {
            if (getObjectModel() == null)
            {
                switch (getClassId())
                {
                    case org.objectfabric.Immutable.BOOLEAN_INDEX:
                        return typeof(bool);
                    case org.objectfabric.Immutable.BYTE_INDEX:
                        return typeof(byte);
                    case org.objectfabric.Immutable.CHARACTER_INDEX:
                        return typeof(char);
                    case org.objectfabric.Immutable.SHORT_INDEX:
                        return typeof(short);
                    case org.objectfabric.Immutable.INTEGER_INDEX:
                        return typeof(int);
                    case org.objectfabric.Immutable.LONG_INDEX:
                        return typeof(long);
                    case org.objectfabric.Immutable.FLOAT_INDEX:
                        return typeof(float);
                    case org.objectfabric.Immutable.DOUBLE_INDEX:
                        return typeof(double);
                    case org.objectfabric.Immutable.STRING_INDEX:
                        return typeof(string);
                    case org.objectfabric.Immutable.DATE_INDEX:
                        return typeof(System.DateTime?);
                    case org.objectfabric.Immutable.BIG_INTEGER_INDEX:
                        return typeof(System.Numerics.BigInteger?);
                    case org.objectfabric.Immutable.DECIMAL_INDEX:
                        return typeof(decimal?);
                    case org.objectfabric.Immutable.BINARY_INDEX:
                        return typeof(byte[]);
                    default:
                        throw new InvalidOperationException();
                }
            }

            return getObjectModel().getClass(getClassId(), getGenericParameters());
        }

        public static bool IsTObject(Type type)
        {
            TType t = From(type);
            return t != null && t.getObjectModel() != null;
        }

        public static bool CanBeTObject(Type type)
        {
            TType t = From(type);
            return t == null || t.getObjectModel() != null;
        }
    }
}