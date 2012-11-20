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

            // TODO
            Console.ReadLine();
        }
    }
}
