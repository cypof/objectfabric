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

The replication mechanism is modelled after source control systems. It tracks and creates immutable representations of changes that can be transmitted and cached as regular Web documents, or could be replicated using e.g. DropBox. Changes are ordered and merged in the background by other clients, and can be stored to keep a resource history.

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
  of.open("ws://test.objectfabric.org/array", function(resource) {
    var array = resource.get();
    // Add a listener on array, called when an element is
    // set to a new value server side
    array.onset(function(i) {
      elem = document.getElementById('td' + i);
      elem.innerHTML = formatNumber(array.get(i));
    });
  });
}
{% endhighlight %}
</div>

<div id="array-2">
{% highlight java %}
// Get live array of numbers through WebSocket
String uri = "ws://test.objectfabric.org/array";
final TArrayLong a = (TArrayLong) workspace.open(uri).get();

// Add a listener on array, called when an element is
// set to a new value server side
a.addListener(new IndexListener() {
    @Override
    public void onSet(int i) {
        switch (i) {
            case 0:
                write("World population: " + a.get(i));
                break;
            case 1:
                write("Internet Users: " + a.get(i));
                break;
        }
    }
});
{% endhighlight %}
</div>

<div id="array-3">
{% highlight csharp %}
// Get live array of numbers through WebSocket
string uri = "ws://test.objectfabric.org/array";
TArray<long> array = (TArray<long>) workspace.Open(uri).Value;

// Add a listener on array, called when an element is
// set to a new value server side
array.Set += i =>
{
    switch (i)
    {
        case 0:
            Write("World population: " + array[i]);
            break;
        case 1:
            Write("Internet Users: " + array[i]);
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
of.open("ws://localhost:8888/room1", function(resource) {
  var messages = resource.get();
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
// Get a room
Resource resource = workspace.open("ws://localhost:8888/room1");
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
// Get a room
Resource resource = workspace.Open("ws://localhost:8888/room1");
TSet<string> messages = (TSet<string>) resource.Value;

// A room is a set of messages. Adding a message to a
// set raises the 'Added' event on all clients who
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

<img class="images" src="/images/images.png"/>

This demo lets you drag images on the screen to see their position replicated in real-time between platforms. It also displays the connection status to let you experiment with turning the server off and on.

[images.zip](https://github.com/downloads/objectfabric/objectfabric/images.zip), (sources: [GWT](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/gwt.sample_images/src/main/java/examples/client/Main.java), [Java](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/java/src/main/java/sample_images/Images.java), [C#](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/csharp/Sample%20Images/MainWindow.xaml.cs))

Things to try:

Launch the server and clients, they should connect and display 'Up to Date'.<br>
Create and drag an image around to see real-time sync.<br>
Kill the server. Clients should alternate between 'Reconnecting...' and 'Waiting retry'.<br>
Create and drag images. Client are still functional.<br>
Kill a client and start it again, it should reload from offline storage.<br>
Restart the server, clients should reconnect and converge.

There is a known bug where synchronization might fail if you delete server's state ('temp' folder).