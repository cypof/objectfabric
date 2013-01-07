---
layout: index
title: ObjectFabric
---

## Versioning for Web Resources

This work explores a very simple idea. Instead of changing a resource in-place, e.g. with an HTTP PUT, a client adds a new version, e.g., with a POST of a [JSON Patch](http://tools.ietf.org/html/draft-ietf-appsawg-json-patch-03) description of the change.

This way to represent resources allows permanent caching, as it is immutable, and low bandwith use, as only deltas are sent when updating a resource. Logging comes for free, as changes build a history, like source control or Big Data systems.

ObjectFabric is a library for change representations. It offers types like map, array, or counter, for which it can create and apply changes, and remove old ones where space matters. It works on most platforms thanks to the [GWT](https://developers.google.com/web-toolkit) and [IKVM](http://www.ikvm.net) recompilers.

## Real-Time Sync

Change representations can be sent over WebSocket to keep a resource in sync between a server and a client. Instead of a static document,

<img class="rest" src="/images/rest.png"/>

a resource becomes dynamic, like Google Docs:

<img class="real-time" src="/images/real-time.png"/>

If the demo gods allow this should show a live example. It fetches an array of numbers and adds a callback to listen for changes. When server code updates a number, OF represents the change, e.g. "index i = x", and send it. On the client, the array is updated and the callback triggers.

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
    <li><a href="#array-4">Server (Node)</a></li>
    <li><a href="#array-5">Server (Java)</a></li>
</ul>

<div id="array-1">
{% highlight javascript %}
// Called when ObjectFabric is loaded
function onof(of) {
  // A workspace loads resources
  var w = new of.workspace();
  w.addURIHandler(new of.WebSocket());

  // Get array of numbers
  of.open("ws://server/array", function(err, resource) {
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
// A workspace loads resources
Workspace w = new JVMWorkspace();
w.addURIHandler(new Netty());

// Get array of numbers
Resource resource = w.open("ws://server/array");
final TArrayLong a = (TArrayLong) resource.get();

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
// A workspace loads resources
Workspace w = new Workspace();
w.AddURIHandler(new WebSocket());

// Get array of numbers
Resource resource = w.Open("ws://server/array");
TArray<long> array = (TArray<long>) .Value;

// Add an event handler on array, called when an
// element is set to a new value server side
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

<div id="array-4">
{% highlight javascript %}
// Store changes in memory
var location = new of.memory();

// A workspace loads resources
var w = new of.workspace();
w.addURIHandler(location);

// Create an array of numbers at /array
w.open("/array", function(err, resource) {
  var array = new of.array(resource, 2);
  resource.set(array);

  // Interpolate number of people every 200ms
  setInterval(function() {
    array.set(0, people());
    array.set(1, internet());
  }, 200);
});

// Create 'ws' WebSocket server
var wss = new WebSocketServer({
  port : 8888
});

// Serve resources in 'location'
var ofs = new of.server();
ofs.addURIHandler(location);
wss.on('connection', function(ws) {
  new of.connection(ofs, ws);
});
{% endhighlight %}
</div>

<div id="array-5">
{% highlight java %}
// Store changes in memory
Memory location = new Memory();

// A workspace loads resources
Workspace w = new JVMWorkspace();
w.addURIHandler(location);

// Create an array of numbers at /array
Resource path = w.open("/array");
TArrayLong array = new TArrayLong(path, 2);
path.set(array);

// Serve resources in 'location'
final Server server = new JVMServer();
server.addURIHandler(location);

// ... Start Netty WebSocket server

for (;;) {
    // Interpolate number of people
    array.set(0, people());
    array.set(1, internet());
    Thread.sleep(200);
}
{% endhighlight %}
</div>
</div>

## Offline Sync

<img class="offline" src='/images/offline.png'/>

When connectivity is down, and for better performance, clients can load resources by using changes they have in cache. They can still store new ones for later synchronization.

Our implementation does not require developers to deal with connection state at all. Resources can always be read and written to, while re-connections are attempted in the background.

This demo lets you drag images on the screen to see their position replicated between platforms. If you kill the server, clients go in offline mode, and try to reconnect while still letting you modify images positions.

If you restart a client while offline, it loads its last state from offline storage. When the server is restarted, clients reconnect and converge.

<img class="images" src="/images/images.png"/>

[images.zip](https://github.com/downloads/objectfabric/objectfabric/images.zip), (sources: [GWT](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/gwt.sample_images/src/main/java/examples/client/Main.java), [Java](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/java/src/main/java/sample_images/Images.java), [C#](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/csharp/Sample%20Images/MainWindow.xaml.cs))

## User Coordination

Concurrent updates of a Web resource require coordination, e.g. ETags with some woodoo on the client in case of conflict, to get the resource again, re-apply changes and retry. Otherwise an HTTP PUT from a user can override updates from another.

Sending changes avoids this complexity. Two apps can get the same resource, e.g. /user123, the first sets "name", and the second "karma". Only one property gets written in each change representation, and no data can be lost.

This code is a Chat application. It allows multiple users to modify a shared set of messages. When a client adds a message to the set, the change gets replicated without overriding others, and triggers a notification on other clients that displays it.

<div id="chat">
<ul>
    <li><a href="#chat-1">JavaScript</a></li>
    <li><a href="#chat-2">Java</a></li>
    <li><a href="#chat-3">C#</a></li>
</ul>

<div id="chat-1">
{% highlight javascript %}
// Get a room
workspace.open("ws://localhost:8888/room1", function(resource) {
  var messages = resource.get();

  jQuery(document).ready(function($) {
    $('body').terminal(function(line, term) {
      // jQuery invokes this callback when user enters text
      messages.add(time() + " - " + name + ": " + line);
    });
  });

  // Display messages that get added to the set
  messages.onadd(function(item) {
    $('body').terminal().echo(item);
  });
});
{% endhighlight %}
</div>

<div id="chat-2">
{% highlight java %}
// Get a room
Resource resource = workspace.open("ws://localhost:8888/room1");
final TSet<String> messages = (TSet) resource.get();

// Display messages that get added to the set
messages.addListener(new AbstractKeyListener<String>() {

    @Override
    public void onPut(String key) {
        System.out.println(key);
    }
});

// Listen for typed messages and add them to the set
for (;;) {
    String line = console.readLine();
    messages.add(time() + " - " + name + ": " + line);
}
{% endhighlight %}
</div>

<div id="chat-3">
{% highlight csharp %}
// Get a room
Resource resource = workspace.Open("ws://localhost:8888/room1");
TSet<string> messages = (TSet<string>) resource.Value;

// Display messages that get added to the set
messages.Added += s =>
{
    Console.WriteLine(s);
};

// Listen for typed messages and add them to the set
for (; ; )
{
    string line = Console.ReadLine();
    messages.Add(Time + " - " + name + ": " + line);
}
{% endhighlight %}
</div>
</div>

## The Store is the Log

When a change is overridden by a new one, it can be deleted, or archived to keep a history. OF simplifies handling of history data by allowing a resource to load as it was at a given point in time. Reading following changes replays events in the application itself, instead of a separate log reader with its own UI and file format.

## More Info

[Getting Started](https://github.com/objectfabric/objectfabric/wiki/Home#wiki-implementations)<br>
[Internals](https://github.com/objectfabric/objectfabric/wiki/Home#wiki-internals)