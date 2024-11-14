# Image available at https://catalog.redhat.com/software/containers/ubi9/openjdk-21-runtime.
FROM registry.access.redhat.com/ubi9/openjdk-21

# Switch to the root user to be able to install the required packages.
USER root

# The argument that holds Red Hat IT's custom certificate's location.
ARG rh_it_root_ca_cert_url
ARG rh_it_root_ca_cert_secondary_url

# install postgresql 15
RUN microdnf install -y dnf
RUN dnf install -y 'dnf-command(config-manager)'
RUN dnf --disablerepo=* -y install https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm
RUN dnf install -y postgresql15-server
RUN dnf install -y postgresql15-contrib

# Add RedHat CAs on OS truststore (check https://certs.corp.redhat.com/ for more details)
RUN curl --output rh_it_root_ca_file.crt --insecure "${rh_it_root_ca_cert_url}" \
    && curl --output rh_it_root_ca_secondary_file.crt --insecure "${rh_it_root_ca_cert_secondary_url}" \
    && mv rh*.crt /etc/pki/ca-trust/source/anchors \
    && update-ca-trust

# Copy the repository contents.
COPY --chown=default:default . /home/default
