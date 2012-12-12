You can get a pre-built version at:
https://github.com/downloads/objectfabric/objectfabric/objectfabric-js.zip

Dependencies:
npm install ws
npm install node-static // Only to serve sample files

Launch:
node server.js

Description:
objectfabric.js is built using GWT & gwt-exporter. For the client
version GWT produces specialized JS for each browser that go in a
/objectfabric folder with a loader named 'objectfabric.nocache.js'.

Debug:
You can get more readable js by compiling objectfabric-js with
an different setting. e.g. run this in folder objectfabric/js

mvn gwt:compile -Dgwt.style=PRETTY
or
mvn gwt:compile -Dgwt.style=DETAILED

It is also possible to debug the underlying GWT code by calling

mvn gwt:run-codeserver

in the same folder and follow the Super Dev Mode instructions:

https://developers.google.com/web-toolkit/articles/superdevmode