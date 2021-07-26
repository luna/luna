FROM ghcr.io/graalvm/graalvm-ce:java11-21.1.0

USER root

ENV LOG_LEVEL=INFO

RUN useradd -u 2000 -c 'Enso Developer' -U -M ensodev

# /opt/enso is the present engine distribution.
# /opt/workdir is a directory for temporary runtime files and logs.
# /volumes/workspace is the root of the mounted workspace which contains all data that must persist when the project is reopened.
# /volumes/workspace/project_root contains the project package.
# /volumes/workspace/data_root contains the data root of the Enso distribution, this is where cached libraries will be located.
# /volumes/workspace/config contains configuration files, currently these are not really used in the Cloud.
# /volumes/workspace/home contains the ENSO_HOME directory, where locally created libraries will be placed.
# Currently, only the /volumes/workspace/project_root needs to be initialized with the project structure when the project is created.
# All other directories are created on-demand.

ADD bin /opt/enso/bin
ADD component /opt/enso/component
ADD lib /opt/enso/lib
ADD editions /opt/enso/editions

RUN mkdir /opt/enso/work
RUN mkdir /opt/enso/logs

ENV ENSO_DATA_DIRECTORY=/volumes/workspace/data_root
ENV ENSO_CONFIG_DIRECTORY=/volumes/workspace/config
ENV ENSO_RUNTIME_DIRECTORY=/opt/enso/work
ENV ENSO_LOG_DIRECTORY=/opt/enso/logs
ENV ENSO_HOME=/volumes/workspace/home

RUN chown -hR ensodev:ensodev /opt/enso
RUN chmod -R u=rX,g=rX /opt/enso
RUN chmod a+x /opt/enso/bin/*
RUN chmod a+rw /opt/enso/work
RUN chmod a+rw /opt/enso/logs

RUN mkdir -p /volumes
RUN chown -hR ensodev:ensodev /volumes
RUN chmod -R u=rwX,g=rwX /volumes

USER ensodev:ensodev

WORKDIR /opt/enso

ENTRYPOINT [ "/opt/enso/bin/docker-entrypoint.sh" ]

EXPOSE 30001
EXPOSE 30002

CMD ["--server", "--daemon", "--rpc-port", "30001", "--data-port", "30002", "--root-id", "00000000-0000-0000-0000-000000000001", "--path", "/volumes/workspace/project_root", "--interface", "0.0.0.0"]
