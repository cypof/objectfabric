using ObjectFabric;
using System;

namespace Sample_Chat
{
    class Program
    {
        static void Main(string[] args)
        {
            // Like opening a browser
            Workspace w = new Workspace();

            // Enables network connections
            w.AddURIHandler(new WebSocketURIHandler());

            // Get a room
            Resource resource = w.Open("ws://localhost:8888/room1");
            TSet<string> messages = (TSet<string>) resource.Value;

            // A room is a set of messages. Adding a message to a
            // set raises the 'onPut' callback on all clients who
            // share the the same URI

            // Display messages that get added to the set
            messages.Added += s =>
            {
                Console.WriteLine(s);
            };

            // Listen for typed messages and add them to the set
            Console.Write("my name? ");
            string me = Console.ReadLine();
            messages.Add("New user: " + me);

            for (; ; )
            {
                string s = Console.ReadLine();
                messages.Add(me + ": " + s);
            }
        }
    }
}
