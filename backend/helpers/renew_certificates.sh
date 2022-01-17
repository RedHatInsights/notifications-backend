#!/usr/bin/env bash

# need to be in the vpn to access Red Hat gitlab
echo "Please check that you are in the VPN! Otherwise the script doesn't work..."

CURRENT_FOLDER=$(pwd)

cd ../src/main/resources/
FINAL_CERTIFICATE_FOLDER=$(pwd)

cd $CURRENT_FOLDER

# generate pem file needed to check validity and application name
openssl pkcs12 -in nonprod-insightsnotifications.pkcs12 -clcerts -nokeys -out publicCert.pem

# certificate validity
EXPIRES_AT=$(openssl x509 -in publicCert.pem -text -noout | grep  "Not After :")
EXPIRES_AT=${EXPIRES_AT#*:}

rm publicCert.pem

# application name
# name is still hard coded. we could also extract it from the certificate file
APPLICATION_NAME="nonprod-insightsnotifications"

read -p "The certificate will expire at $EXPIRES_AT. Do you still want to renew the certificate? [y/n]" -n 1 -r
echo    # (optional) move to a new line
if [[ $REPLY =~ ^[Yy]$ ]]
then
    # do dangerous stuff
    echo "Installing packages necessary for certificate renewal..."
    sudo dnf install openssl certmonger openldap-clients


    cd /tmp/
    
    echo "Cloning the utility project..."
    rm -rf /tmp/utility/
    git clone https://gitlab.corp.redhat.com/it-iam/utility.git
    cd utility/PKI
    
    echo "Creating new certificate based on the existing .pkcs12 one..."
    sh get_rhcs_app_cert.sh ${APPLICATION_NAME} prod

    cd /tmp/

    # mv files to project helpers folder
    echo "Moving new certificate file to helpers folder..."
    mv nonprod-insightsnotifications.pkcs12 $CURRENT_FOLDER

    echo "Moving jks file to src/main/resources folder..."
    #mv nonprod-insightsnotifications.crt $FINAL_CERTIFICATE_FOLDER
    #mv nonprod-insightsnotifications.csr $FINAL_CERTIFICATE_FOLDER
    mv nonprod-insightsnotifications.jks $FINAL_CERTIFICATE_FOLDER
    #mv nonprod-insightsnotifications.key $FINAL_CERTIFICATE_FOLDER
fi


