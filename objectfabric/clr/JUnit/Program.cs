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
using System.Diagnostics;

namespace JavaTests
{
    class Program
    {
        static void Main( string[] args )
        {
            TestsPlatformAdapter.setInstance( new TestsPlatformAdapter() );

            java.math.BigDecimal value = new java.math.BigDecimal( "45000" );
            byte[] bytes = value.unscaledValue().toByteArray();
            int scale = value.scale();

            //

            decimal b = (decimal) new ObjectFabric.DecimalConverter( true, bytes, scale );
            Debug.Assert( value.toString() == b.ToString() );

            Run( new org.objectfabric.SerializationTest() );
            //Run( new DistributedSerializationTest() );

            //JavaTestsRunner.Run();

            Console.ReadLine();
        }

        static void Run( org.objectfabric.SerializationTest test )
        {
            test.run( true, true, ", CSharp, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null" );
            test.run( true, false, ", CSharp, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null" );
            test.run( false, true, ", CSharp, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null" );
            test.run( false, false, ", CSharp, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null" );
        }
    }
}
