---
layout: post
title: Consistency and Separation of Concerns
---

ObjectFabric synchronizes data in an eventually consistent way. Updates are accepted immediately, and conflicts resolved later between versions in a deterministic way on all machines. This resolution is currently arbitrary, the system just picks a version. The API will need to exposes conflicts for user resolution in a future version.

It is currently the only model. If strong consistency needs to be added at some point, it will be through a separate component, an "Arbiter" responsible for picking a particular "truth". Updates propagate to this arbiter in an eventually consistent way, and propagate back to the client when a particular ordering has been decided.

For the usual shopping cart example, it would mean the user clicks an item and the interface would show it as a pending transaction. This information is stored and would then eventually propagate to, e.g. a database in the cloud. If the item turns out to be still available then, it is marked as purchased, and this new information is then propagated back, possibly later if the user lost connectivity in the meantime. His application would then show the transaction as successful.

There are other examples of separating data propagation and arbitration, like [Cassandra](http://cassandra.apache.org) vs. [Cages](http://code.google.com/p/cages), or this [paper](http://research.microsoft.com/apps/pubs/default.aspx?id=155638) from Microsoft.
