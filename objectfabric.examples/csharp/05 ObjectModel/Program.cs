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

            TDictionary<object, object> map = (TDictionary<object, object>) workspace.Open("ws://localhost:8888/map").Value;

            map.Added += key =>
            {
                Console.WriteLine("Added: " + map[key]);
            };

            map["blah"] = "new";

            // TODO finish

            Console.ReadLine();
        }
    }
}
