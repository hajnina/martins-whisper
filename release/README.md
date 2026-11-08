# Rolling `latest` release

The `release` jobs in `.github/workflows/android.yml` and `native-build.yml` are thin: checkout,
download the APK artifact, `docker build release/`, `docker run` it. **All release logic lives
here**, so it can be changed without touching `.github/workflows/`:

- `publish.sh <android|native> <apk-dir>`: asset names, release notes, extra assets (hook block).
- `Dockerfile`: tools available to the script (alpine + bash, gh, git, jq, curl).

Dry run locally: `docker build -t mw-release release && docker run --rm -e DRY_RUN=1 -e GITHUB_SHA=x -v "$PWD:/work" mw-release native /work/apk`
(with a dummy `apk/app-debug.apk`).

Trust note: this runs after a push to the default branch with `contents: write`, so whoever can
merge to master can change what it does. It never runs on pull requests.
