using System;
using ObjectFabric;

namespace Examples
{
    class HelloWorld
    {
        static void Main(string[] args)
        {
            // Like opening a browser
            Workspace w = new Workspace();

            // Enable network connections
            w.AddURIHandler(new WebSocketURIHandler());

            // Get live array of numbers through WebSocket
            string uri = "ws://test.objectfabric.org/array";
            TArray<long> a = (TArray<long>) w.resolve(uri).get();

            // Add a listener on array, called when an element is
            // set to a new value server side
            a.Set += i => { Console.WriteLine(a[i]); };
        }
    }
}
