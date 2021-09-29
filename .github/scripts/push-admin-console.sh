#!/usr/bin/env bash
# When the builds succeeds, we need to place the results in the backend META-INF code and commit it.
# Delete whatever is there and add the new build.

TARGET="backend/src/main/resources/META-INF/resources/internal"

rm -rf "${TARGET}"
mkdir -p "${TARGET}"
cp -r admin-console/src/main/webapp/build/* "${TARGET}"

git config --global user.name "${GITHUB_ACTOR}[automatic action]"
git config --global user.email "${GITHUB_ACTOR}@users.noreply.github.com"
git add -f "${TARGET}"
git commit -m "Updating admin-console build"
git push
