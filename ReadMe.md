## Versioning for Web Resources

This work explores a simple idea. Instead of changing a resource in-place, e.g. with an HTTP PUT, a client adds a new version, e.g., with a POST of a description of the change.

This way to represent resources allows permanent caching, as it is immutable, and low bandwith use, as only deltas are sent when updating a resource. Logging comes for free, as changes form a history, like source control systems.

ObjectFabric is a library for change representations. It offers types like map, array, or counter, for which it can create and apply changes, and remove old ones where space matters. It is internally based on a STM, which provides a precise and efficient way to define changes using transactions.

## Real-Time Sync

Change representations can be sent over WebSocket to keep a resource in sync between a server and a client. Instead of a static document,

<img class="rest" src="/images/rest.png"/>

a resource becomes dynamic, like Google Docs:

<img class="real-time" src="/images/real-time.png"/>

This example between a Java server and a JavaScript client fetches an array of numbers and adds a callback to listen for changes. When server code updates a number, OF represents the change, e.g. "index i = x", and send it. On the client, the array is updated and the callback triggers.

```
// JavaScript - Called when ObjectFabric is loaded
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
```

```
// Java - A workspace loads resources
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
```

## Offline Sync

<img class="offline" src='/images/offline.png'/>

When connectivity is down, and for better performance, clients can load resources by using changes they have in cache. They can still store new ones for later synchronization.

Our implementation does not require developers to deal with connection state at all. Resources can always be read and written to, while re-connections are attempted in the background.

This demo lets you drag images on the screen to see their position replicated between platforms. If you kill the server, clients go in offline mode, and try to reconnect while still letting you modify images positions.

If you restart a client while offline, it loads its last state from offline storage. When the server is restarted, clients reconnect and converge.

<img class="images" src="/images/images.png"/>

## User Coordination

Concurrent updates of a Web resource require coordination, e.g. ETags with some woodoo on the client in case of conflict, to get the resource again, re-apply changes and retry. Otherwise an HTTP PUT from a user can override updates from another.

Sending changes avoids this complexity. Two apps can get the same resource, e.g. /user123, the first sets "name", and the second "karma". Only one property gets written in each change representation, and no data can be lost.

This code is a Chat application. It allows multiple users to modify a shared set of messages. When a client adds a message to the set, the change gets replicated without overriding others, and triggers a notification on other clients that displays it.

```
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
```

## Users

This project has been deployed at the NASA Ames Research Center. [Read more...](https://github.com/objectfabric/objectfabric/wiki/Users)

<img class="images" src="/images/nasa.png"/>

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
