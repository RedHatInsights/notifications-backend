#!/usr/bin/env bash

DEPLOY_REPO="https://${GITHUB_ACTOR}:${1}@github.com/josejulio/notifications-frontend-admin-build.git"
BRANCH="main"

# remove
mkdir -p admin-console/src/main/webapp/build/
cd admin-console/src/main/webapp/build/ || exit

git config --global user.name "${GITHUB_ACTOR}"
git config --global user.email "${GITHUB_ACTOR}@users.noreply.github.com"

if git ls-remote --exit-code "${DEPLOY_REPO:-$REPO}" ${BRANCH} &>/dev/null; then
  # Handle where the target branch exists
  git clone --bare --branch ${BRANCH} --depth 1 "${DEPLOY_REPO:-$REPO}" .git
else
  # Handle where the repo does not have a default branch (i.e. an empty repo)
  git init -b "${BRANCH}"
  git remote add origin "${DEPLOY_REPO}"
  git commit --allow-empty -m "Initial commit"
fi

git config core.bare false
git add -A
git commit -m "${COMMIT_MESSAGE}"
git push origin ${BRANCH}
