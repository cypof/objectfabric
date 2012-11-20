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
using System.Collections.Generic;
using System.Text;

namespace JavaTests
{
    class JavaTestsRunner
    {
        public static void Run()
        {
            List<java.lang.Class> classes = new List<java.lang.Class>();

            foreach( Type type in typeof( org.objectfabric.Transaction ).Assembly.GetTypes() )
            {
                java.lang.Class c = type;

                if( c != null && c.getName().StartsWith( "org.objectfabric" ) )
                    classes.Add( type );
            }

            //AppDomain.CurrentDomain.UnhandledException += new UnhandledExceptionEventHandler( CurrentDomain_UnhandledException );

            org.junit.runner.JUnitCore core = new org.junit.runner.JUnitCore();
            core.addListener( new Listener() );
            core.run( classes.ToArray() );

            Console.WriteLine( "Success!" );
        }

        class Listener : org.junit.runner.notification.RunListener
        {
            public override void testStarted( org.junit.runner.Description description )
            {
                string name = description.getDisplayName();

                if( !name.StartsWith( "initializationError" ) )
                    Console.WriteLine( name );
            }
        }

        static void CurrentDomain_UnhandledException( object sender, UnhandledExceptionEventArgs e )
        {
            Console.WriteLine( e.ExceptionObject );
            Console.ReadKey();
        }
    }
}
