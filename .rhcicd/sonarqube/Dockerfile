# Image available at https://catalog.redhat.com/software/containers/ubi9/openjdk-21-runtime.
FROM registry.access.redhat.com/ubi9/openjdk-21

# Switch to the root user to be able to install the required packages.
USER root

# This argument holds the new password for the "cacerts" keystore.
ARG cacerts_keystore_password
# The argument that holds Red Hat IT's custom certificate's location.
ARG rh_it_root_ca_cert_url
ARG rh_it_root_ca_cert_secondary_url
ARG rhcs_root_ca_cert_url

# install postgresql 15
RUN microdnf install -y dnf
RUN dnf install -y 'dnf-command(config-manager)'
RUN dnf --disablerepo=* -y install https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm
RUN dnf install -y postgresql15-server
RUN dnf install -y postgresql15-contrib


# 1. Install the UUID package which is required for the scripts.
# 2. Change the default "cacert" keystore's password to a custom one for more
#    security.
# 3. Import Red Hat IT's custom self signed certificate in the general
#    "cacerts" file.
# 4. Remove the certificate file.
RUN mkdir --parents "/var/cache/yum/metadata" \
    && microdnf install --assumeyes uuid-1.6.2-55.el9.x86_64 \
    && microdnf clean all \
    && readonly rh_it_root_ca_file="$(mktemp)" \
    && readonly rh_it_root_ca_secondary_file="$(mktemp)" \
    && readonly rhcs_root_ca_file="$(mktemp)" \
    && curl --output "${rh_it_root_ca_file}" --insecure "${rh_it_root_ca_cert_url}" \
    && curl --output "${rh_it_root_ca_secondary_file}" --insecure "${rh_it_root_ca_cert_secondary_url}" \
    && curl --output "${rhcs_root_ca_file}" --insecure "${rhcs_root_ca_cert_url}" \
    && keytool \
        -cacerts \
        -new "${cacerts_keystore_password}" \
        -storepasswd \
        -storepass "changeit" \
    && keytool \
         -alias "RH-IT-Root-CA" \
         -cacerts \
         -file "${rh_it_root_ca_file}" \
         -importcert \
         -noprompt \
         -storepass "${cacerts_keystore_password}" \
         -trustcacerts \
    && keytool \
         -alias "RH-IT-Root-CA-SECONDARY" \
         -cacerts \
         -file "${rh_it_root_ca_secondary_file}" \
         -importcert \
         -noprompt \
         -storepass "${cacerts_keystore_password}" \
         -trustcacerts \
    && keytool \
         -alias "RHCS-Root-CA" \
         -cacerts \
         -file "${rhcs_root_ca_file}" \
         -importcert \
         -noprompt \
         -storepass "${cacerts_keystore_password}" \
         -trustcacerts \
    && rm "${rh_it_root_ca_file}" \
    && rm "${rh_it_root_ca_secondary_file}"

# Copy the repository contents.
COPY --chown=default:default . /home/default
