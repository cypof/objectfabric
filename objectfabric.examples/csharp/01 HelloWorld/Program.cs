using System;
using ObjectFabric;

namespace Examples
{
    class HelloWorld
    {
        static void Main(string[] args)
        {
            Workspace workspace = new Workspace();
            workspace.AddURIHandler(new WebSocketURIHandler());

            /*
             * Fetch resource from server. (Server java\src\main\java\launchfirst\SamplesServer.java must be running)
             */
            object value = workspace.Resolve("ws://localhost:8888/helloworld").Get();
            Console.WriteLine(value);

            Console.ReadLine();
        }
    }
}
