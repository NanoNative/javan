FROM ubuntu:24.04 AS rootfs

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
    mkdir -p /rootfs/opt /rootfs/workspace /rootfs/tmp /rootfs/etc/ssl/certs; \
    chmod 0777 /rootfs/workspace; \
    chmod 1777 /rootfs/tmp; \
    mv "javan-${JAVAN_VERSION}-linux-${package_arch}" /rootfs/opt/javan; \
    if [ -f /etc/ssl/certs/ca-certificates.crt ]; then \
      cp /etc/ssl/certs/ca-certificates.crt /rootfs/etc/ssl/certs/ca-certificates.crt; \
    fi; \
    ldd /rootfs/opt/javan/bin/javan \
      | awk '/=>[[:space:]]+\// { print $3 } $1 ~ /^\// { print $1 }' \
      | sort -u \
      | while IFS= read -r library; do \
          target="/rootfs${library}"; \
          mkdir -p "$(dirname "$target")"; \
          cp "$library" "$target"; \
        done; \
    chroot /rootfs /opt/javan/bin/javan --version

FROM scratch

COPY --from=rootfs /rootfs/ /
ENV PATH="/opt/javan/bin"
WORKDIR /workspace
ENTRYPOINT ["/opt/javan/bin/javan"]
CMD ["--help"]
RUN ["/opt/javan/bin/javan", "--version"]
