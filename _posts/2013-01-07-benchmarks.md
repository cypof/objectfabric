---
layout: post
title: Benchmarks
---

Both benchmarks consist of a simple loop updating elements of an array of numbers (doubles on Java). Changes to the array are tracked and serialized into binary representations, which are then stored in memory or on disk, or sent over a network. Performance is similar for arrays of other primitives, and for maps and sets.

##Java
The change tracking system can process 23 million array elements per second, which produces 184MB/s using the binary serializer. OF has a mechanism to properly skip data it cannot persist or transmit in time, by coalescing changes made to the same field or map key.

Using Netty between two processes on the same box, the networking stack can transfer 120MB/s, and the disk backend about 40MB/s. The disk tests ran on a SSD that can handle higher rates, so this is probably the limit for the current implementation.

##JavaScript
Change tracking rate is 40,000 array elements per second, which produces 260KB/s. Both WebSocket and IndexedDB back-ends can handle this rate easily.

##Setup
Intel i7 2630 2GHz, JDK 1.7.0_09, Node.js 0.8.15. For better change tracking performance on the JVM, the loop is split in an inner and outer loop. The inner loop is run using the [atomic]( https://github.com/objectfabric/objectfabric/blob/master/objectfabric.examples/java/src/main/java/part09/STM.java) method to create batches of a thousand changes each. Benchmarks are run 10 times in the same JVM or Node instance to allow proper optimization.
