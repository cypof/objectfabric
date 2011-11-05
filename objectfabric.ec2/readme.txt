This project helps deploying and debugging an ObjectFabric server to an Amazon EC2 instance.
Check this tutorial on our Wiki:

  https://github.com/ObjectFabric/ObjectFabric/wiki/Deploy-and-debug-a-server-on-Amazon-EC2

Amazon provides a default Linux configuration with java pre-installed:

  http://aws.amazon.com/amazon-linux-ami

Once you got an instance of this AMI started (e.g. on an EBS), use the following ant tasks to
deploy your server on the AMI.

Ant tasks in build.xml are meant to be invoked from another build file, in your own project.
Your build file needs to set several properties before calling the tasks, like public DNS and
private key of your instance, which are given to you by Amazon when starting the EC2 instance.
ObjectFabric does not use a container like Spring or Tomcat by default so you can simply specify
the location of your classes and main class. C.f. objectfabric.examples/build.xml for an example.

Task "open command line and debug port" opens a ssh connection to the Amazon EC2 instance,
and forwards a port to enable remote debugging. The first time you connect, ssh will add the
EC2 instance to known_hosts. You can launch the "debug.launch" configuration in Eclipse to debug
your server once deployed and started.

Task "deploy and launch" synchronizes the local folder "deployed" to the instance's home folder,
and launches your server by invoking "deployed/start.sh". WARNING: synchronization of the deployed
folder is full: it deletes all files on the AMI that are not present on your developer machine.
Make sure you do not store data files and logs in the "deployed" folder. By default, stdout and
stderr go to deployed.log in the home folder.

From the command line, you can run "deployed/install_daemon.sh" to add your server to the AMI init
file "rc.local" to let your server start when the machine boots. For security the JVM will not run
as root so for now you cannot open ports like 80. We are working on securely opening the ports at
startup.