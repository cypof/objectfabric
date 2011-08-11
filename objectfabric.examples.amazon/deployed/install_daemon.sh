installed=`grep -c "/home/ec2-user/deployed" /etc/rc.d/rc.local`

if [ $installed -eq 0 ]
    then
        echo "Installing as daemon."
        
        touch /home/ec2-user/deployed.boot.log
        chmod a+w /home/ec2-user/deployed.boot.log
        
        touch /home/ec2-user/deployed.log
        chmod a+w /home/ec2-user/deployed.log
        
        sudo chmod o+w /etc/rc.d/rc.local
        echo -e "\n/home/ec2-user/deployed/boot.sh > /home/ec2-user/deployed.boot.log">>/etc/rc.d/rc.local
        sudo chmod o-w /etc/rc.d/rc.local
fi