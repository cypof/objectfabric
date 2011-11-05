main=#myMainClass#
cp=#classPath#
log=/home/ec2-user/deployed.log
cd /home/ec2-user/deployed
echo "Starting server as $USER." >> $log
nohup java -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n -cp $cp $main </dev/null >> $log 2>> $log &
echo "Started server as $USER."