# Runtime for the rolling "latest" release step. Add tools here (jq, zip, curl, ...);
# CI builds this image fresh each run, so edits take effect on the next push.
FROM alpine:3.20
RUN apk add --no-cache bash github-cli git jq curl
WORKDIR /work
ENTRYPOINT ["bash", "/work/release/publish.sh"]
