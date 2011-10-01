pushd .
cd /home/ec2-user/deployed
echo $USER
su - ec2-user /home/ec2-user/deployed/start.sh
echo "Last command $?"
popd