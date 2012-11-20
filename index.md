---
layout: index
title: ObjectFabric
---

## REST 2.0 = REST + Real Time + Offline

This project is an attempt to combine REST's power and simplicity with recent developments like WebSockets and eventual consistency algorithms. REST architectures gave us scalability over stateless servers, easy resource caching, and the familiar URI + verb API. Today, more and more applications need to go further.

## + Real Time

A REST resource fetched from a server is a static document.

<img class="rest" src="/images/rest.png"/>

REST 2.0 resources keep track and synchronize changes to remain up-to date, like Google Docs. Applications otherwise look like regular REST when resources are not changing. The name "REST 2.0" tries to convey this by analogy with "Web 2.0", which are still Web sites, but use Ajax to update parts of pages in real-time.

<img class="real-time" src="/images/real-time.png"/>

The replication mechanism is modelled after source control systems. It tracks and creates immutable representations of changes that can be transmitted and cached as regular Web documents. Changes are ordered and merged in the background by other clients, and can be stored to keep a resource history.

## + Offline

<img class="offline" src='/images/offline.png'/>

Resources can be cached when connectivity is not available, and for better performance. REST 2.0 applications do not have to deal directly with connection state. Resources  can always be read and written to, while re-connections are attempted in the background. The same eventual consistency algorithm reconciles versions between servers, live clients and offline stores.

## Demo - Streaming

If the demo gods allow, this should show live data pushed by our test server.
<table>
  <tr>
    <td class="demo">World Population:</td>
    <td class="demo" id='td0'>Connecting...</td>
    <td></td>
  </tr>
  <tr>
    <td class="demo">Internet Users:</td>
    <td class="demo" id='td1'>Connecting...</td>
    <td></td>
  </tr>
</table>

<div id="array">
<ul>
    <li><a href="#array-1">JavaScript</a></li>
    <li><a href="#array-2">Java</a></li>
    <li><a href="#array-3">C#</a></li>
</ul>

<div id="array-1">
{% highlight javascript %}
// Called when ObjectFabric is loaded
function onof(of) {
  // Get live array of numbers through WebSocket
  of.resolve("ws://test.objectfabric.org/array").get(function(a) {
    // Add a listener on array, called when an element is
    // set to a new value server side
    a.onset(function(i) {
      elem = document.getElementById('td' + i);
      elem.innerHTML = formatNumber(a.get(i));
    });
  });
}
{% endhighlight %}
</div>

<div id="array-2">
{% highlight java %}
// Like opening a browser
Workspace w = new JVMWorkspace();

// Enable network connections
w.addURIHandler(new NettyURIHandler());

// Get live array of numbers through WebSocket
String uri = "ws://test.objectfabric.org/array";
final TArrayLong a = (TArrayLong) w.resolve(uri).get();
final NumberFormat format = NumberFormat.getIntegerInstance();

// Add a listener on array, called when an element is
// set to a new value server side
a.addListener(new IndexListener() {

    @Override
    public void onSet(int i) {
        String n = format.format(a.get(i));

        switch (i) {
            case 0:
                System.out.println("World population: " + n);
                break;
            case 1:
                System.out.println("Internet Users: " + n);
                break;
        }
    }
});
{% endhighlight %}
</div>

<div id="array-3">
{% highlight csharp %}
// Like opening a browser
Workspace w = new Workspace();

// Enable network connections
w.AddURIHandler(new WebSocketURIHandler());

// Get live array of numbers through WebSocket
string uri = "ws://test.objectfabric.org/array";
TArray<long> a = (TArray<long>) w.Resolve(uri).Get();

// Add a listener on array, called when an element is
// set to a new value server side
a.Set += i =>
{
    switch (i)
    {
        case 0:
            Console.WriteLine("World population: " + a[i]);
            break;
        case 1:
            Console.WriteLine("Internet Users: " + a[i]);
            break;
    }
};
{% endhighlight %}
</div>
</div>

## Demo - Chat

Not packaged yet, but can be run from the source.

<div id="chat">
<ul>
    <li><a href="#chat-1">JavaScript</a></li>
    <li><a href="#chat-2">Java</a></li>
    <li><a href="#chat-3">C#</a></li>
</ul>

<div id="chat-1">
{% highlight javascript %}
// Called when ObjectFabric is loaded
function onof(of) {
// Get a room
of.resolve("ws://localhost:8888/room1").get(function(messages) {
  var me = "";

  jQuery(document).ready(function($) {
    $('body').terminal(function(line, term) {
      // This callback is invoked when user enters text, first
      // the name 'me', then messages
      if (me == "") {
        me = line;
        // A room is a set of messages. Adding a message to a
        // set raises the 'onadd' callback on all clients who
        // share the the same URI
        messages.add("New user: " + me);
      } else
        messages.add(me + ": " + line);
    }, {
      greetings : "JavaScript Chat\nmy name? "
    });
  });

  // Display messages that get added to the set
  messages.onadd(function(item) {
    $('body').terminal().echo(item);
  });
});
}
{% endhighlight %}
</div>

<div id="chat-2">
{% highlight java %}
// Like opening a browser
Workspace w = new JVMWorkspace();

// Enables network connections
w.addURIHandler(new NettyURIHandler());

// Get a room
Resource resource = w.resolve("ws://localhost:8888/room1");
final TSet<String> messages = (TSet) resource.get();

// A room is a set of messages. Adding a message to a
// set raises the 'onPut' callback on all clients who
// share the the same URI

// Display messages that get added to the set
messages.addListener(new AbstractKeyListener<String>() {

    @Override
    public void onPut(String key) {
        System.out.println(key);
    }
});

// Listen for typed messages and add them to the set
System.out.print("my name? ");
String me = console.readLine();
messages.add("New user: " + me);

for (;;) {
    String s = console.readLine();
    messages.add(me + ": " + s);
}
{% endhighlight %}
</div>

<div id="chat-3">
{% highlight csharp %}
// Like opening a browser
Workspace w = new Workspace();

// Enables network connections
w.AddURIHandler(new WebSocketURIHandler());

// Get a room
Resource resource = w.Resolve("ws://localhost:8888/room1");
TSet<string> messages = (TSet<string>) resource.Get();

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
{% endhighlight %}
</div>
</div>

## Demo - Images

This one lets you drag images on the screen to see their position replicated in real-time between processes and platforms. It also displays the current connection status to let you experiment with turning the server off and back on. Clients can be closed and restarted from offline storage. When the server is back, they reconnect and synchronize with each other.

## Try

blah
h