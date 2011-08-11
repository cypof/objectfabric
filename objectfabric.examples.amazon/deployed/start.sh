cd /home/ec2-user/deployed
log=/home/ec2-user/deployed.log
echo "Starting server as $USER." >> $log
nohup java -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n -cp classes:objectfabric-1.0.jar server.Main >> $log &