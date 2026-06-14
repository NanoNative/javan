FROM ubuntu:24.04 AS extract

ARG JAVAN_VERSION
ARG TARGETARCH

WORKDIR /tmp/javan
COPY dist/release/ /tmp/dist/release/
RUN set -eu; \
    case "$TARGETARCH" in \
      amd64) package_arch=x64 ;; \
      arm64) package_arch=aarch64 ;; \
      *) printf '%s\n' "Unsupported target architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    archive="/tmp/dist/release/javan-${JAVAN_VERSION}-linux-${package_arch}.tar.gz"; \
    test -f "$archive"; \
    tar -xzf "$archive"; \
    mkdir -p /opt; \
    mv "javan-${JAVAN_VERSION}-linux-${package_arch}" /opt/javan; \
    mkdir -p /workspace; \
    chmod 0777 /workspace; \
    /opt/javan/bin/javan --version

FROM gcr.io/distroless/cc-debian12:nonroot

COPY --from=extract /opt/javan /opt/javan
COPY --from=extract --chown=65532:65532 /workspace /workspace
ENV PATH="/opt/javan/bin:${PATH}"
WORKDIR /workspace
ENTRYPOINT ["/opt/javan/bin/javan"]
CMD ["--help"]
RUN ["/opt/javan/bin/javan", "--version"]
