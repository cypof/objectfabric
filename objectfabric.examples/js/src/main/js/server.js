var of = require('./objectfabric.node.js');
var WebSocketServer = require('ws').Server;
var static = require('node-static');
var http = require('http');

// Resources will be stored here
var location = new of.memory();
// var location = new of.filesystem("temp");

// Create workspace to add resources, this needs to be
// done only once if location is file system
var workspace = new of.workspace();
workspace.addURIHandler(location);

workspace.open("/helloworld", function(err, resource) {
  resource.set("Hello World!");
});

workspace.open("/string", function(err, resource) {
  resource.set("{\"key\": \"value\"}");
});

workspace.open("/number", function(err, resource) {
  resource.set(1);
});

workspace.open("/set", function(err, resource) {
  var set = new of.set(resource);
  set.add("blah");
  resource.set(set);
});

workspace.open("/map", function(err, resource) {
  var map = new of.map(resource);
  map.put("example key", "value");
  map.put(42, true);
  resource.set(map);
});

workspace.open("/arrayOfInt", function(err, resource) {
  var ints = new of.arrayInteger(resource, 10);
  ints.set(5, 1);
  resource.set(ints);
});

workspace.open("/counter", function(err, resource) {
  var counter = new of.counter(resource);
  counter.add(1);
  resource.set(counter);
});

// Create WS server
var wss = new WebSocketServer({
  port : 8888
});

// Serve resources in location
var ofServer = new of.server();
ofServer.addURIHandler(location);
wss.on('connection', function(ws) {
  new of.connection(ofServer, ws);
});

console.log("Serving OF resources on 8888");

// Serve static files for samples
var fileServer = new static.Server('./clients');
http.createServer(function(request, response) {
  request.addListener('end', function() {
    fileServer.serve(request, response);
  });
}).listen(4000); // 8080 used by node-inspector

console.log("Go to http://localhost:4000/01helloworld.html");