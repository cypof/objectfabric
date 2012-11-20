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
    /// <summary>
    /// Used to replace methods bodies, e.g. java.lang.Object.toString().
    /// </summary>
    class MethodTemplates
    {
        public void NotImplementedExceptionMethod()
        {
            throw new NotImplementedException();
        }

        public override string ToString()
        {
            return base.ToString();
        }

        public static string ToStringStatic( object value )
        {
            return value.ToString();
        }

        public static char[] copyOf( char[] original, int newLength )
        {
            char[] dest = new char[newLength];
            Array.Copy( original, 0, dest, 0, Math.Min( original.Length, newLength ) );
            return dest;
        }

        public static T MapException<T>( Exception ex, int mode ) where T : Exception
        {
            return (T) ex;
        }

        public static Exception UnmapException( Exception ex )
        {
            return ex;
        }
    }

    class ExceptionTemplates : Exception
    {
        public ExceptionTemplates()
        {
        }

        public ExceptionTemplates( string message )
            : base( message )
        {
        }

        public ExceptionTemplates( Exception inner )
            : base( inner.Message, inner )
        {
        }

        public ExceptionTemplates( string message, Exception inner )
            : base( message, inner )
        {
        }

        public override string ToString()
        {
            return base.ToString();
        }
    }
}