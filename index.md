---
layout: index
title: ObjectFabric
---

## Versioning for Web Resources

This work is an exploration of a very simple idea. Instead of changing a resource in-place, e.g. with an HTTP PUT, a client adds a new version, e.g., with a POST of a [JSON Patch](http://tools.ietf.org/html/draft-ietf-appsawg-json-patch-03) description of the change. It offers a surprising set of benefits. ObjectFabric is a library that makes this type of architecture simple.

## Real-Time Sync

By sending changes which are deltas relative to previous versions, an application can efficiently keep a resource in sync with a server. Instead of a static document,

<img class="rest" src="/images/rest.png"/>

a resource becomes dynamic, like Google Docs:

<img class="real-time" src="/images/real-time.png"/>

OF provides types, like map, array, or counter, for which it can represent and send changes automatically, when instances are updated by application code.

If the demo gods allow, this should show live data. It fetches an array of numbers and adds a callback to listen for changes. Server code is a simple loop that updates array items. Changed items are represented, e.g. "index i = x", applied on the client, and trigger the callback.

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

## Offline Sync

<img class="offline" src='/images/offline.png'/>

When connectivity is down, and for better performance, clients can load resources by using changes they have in cache. They can still store new ones for later synchronization.

Our implementation does not require developers to deal with connection state at all. Resources can always be read and written to, while re-connections are attempted in the background.

This demo lets you drag images on the screen to see their position replicated between platforms. If you kill the server, clients go in offline mode, and try to reconnect while still letting you modify images positions.

If you restart a client while offline, it loads its last state from offline storage. When the server is restarted, clients reconnect and converge.

<img class="images" src="/images/images.png"/>

[images.zip](https://github.com/downloads/objectfabric/objectfabric/images.zip), (sources: [GWT](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/gwt.sample_images/src/main/java/examples/client/Main.java), [Java](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/java/src/main/java/sample_images/Images.java), [C#](https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/csharp/Sample%20Images/MainWindow.xaml.cs))

## Users Coordination

Concurrent updates of a Web resource require coordination, e.g. ETags with some woodoo on the client in case of conflict, to get the resource again, re-apply changes and retry. Otherwise an HTTP PUT from a user can override updates from another.

Sending changes avoids this complexity. Two apps can get the same resource, e.g. /user123, the first sets "name", and the second "karma". Only one property gets written in each change representation, and no data can be lost.

This demo is a Chat application. It allows multiple users to modify a shared set of messages. When a client adds a message to the set, the change gets replicated without overriding others, and triggers a notification on other clients that displays it.

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
}
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

By default OF deletes changes when overridden by new ones, to save space. For some applications it might make more sense to configure it to keep everything. It builds a history in the data store itself, instead of the usual log files. Source control systems get it right.

By picking a given change representation instead of the latest known, OF can load a resource at a given point in time. Reading following changes makes it possible to replay events from there, e.g. in a UI, as if it was receiving them live from the network.

## Optimal Caching & Bandwidth Use

Changes are stored in an append-only way, so their representation is immutable, which is great by itself because it makes [Rich Hickey](https://twitter.com/fakerichhickey) happy. [Immutability](http://www.infoq.com/presentations/Value-Values), for Web resources.

There is no need for cache expiration, data can be kept as long as there is space. Bandwith usage is also optimized because data is never sent twice, only deltas compared to previous versions.

## Here be Jargon

<div class="jargon">
<ul>
    <li>RESTish. This programming model exhibits the familiar URI + verb API, properties like scalability over stateless servers, resource cacheability, and lets applications be written in a HATEOAS style.</li>

    <li>Scales through eventual consistency. OF orders changes by adding a header to their representations with vector clock information similar to NoSQL stores. One of the benefits is that clients still converge if changes get re-ordered during propagation through multiple servers in the cloud.</li>

    <li>Avoids the Slow Consumer problem, as changes to the same data coalesce when clients cannot follow. Each client get updates at an optimal latency for its bandwidth.</li>

	<li>Idempotent. Instead of making a request/response to a server, e.g. to create an item, creating it locally and letting the change replicate guaranties it will only be created once. Local actions always succeed, and the system can determine if a change has already been replicated.</li>

	<li>Compatible with existing infrastructure, unlike versioning methods such as <a href="http://tools.ietf.org/html/rfc5789">HTTP PATCH</a>, which requires special server support.</li>
</ul>
</div>

## More Info

[Getting Started](https://github.com/objectfabric/objectfabric/wiki/Home#wiki-implementations)<br>
[Internals](https://github.com/objectfabric/objectfabric/wiki/Home#wiki-internals)