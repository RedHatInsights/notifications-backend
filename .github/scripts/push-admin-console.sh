#!/usr/bin/env bash
set -x

DEPLOY_REPO="git@github.com:RedHatInsights/notifications-frontend-admin-build.git"
BRANCH="main"

cd admin-console/src/main/webapp/build/ || exit

git config --global user.name "${GITHUB_ACTOR}"
git config --global user.email "${GITHUB_ACTOR}@users.noreply.github.com"

if git ls-remote --exit-code ${DEPLOY_REPO:-$REPO} ${BRANCH} &>/dev/null; then
  # Handle where the target branch exists
  git clone --bare --branch ${BRANCH} --depth 1 ${DEPLOY_REPO:-$REPO} .git
else
  # Handle where the repo does not have a default branch (i.e. an empty repo)
  git init
  git remote add origin ${DEPLOY_REPO:-$REPO}
  git commit --allow-empty -m "Initial commit"
  git push origin master
  git checkout -b ${BRANCH}
fi

git config core.bare false
git add -A
git commit -m "${TRAVIS_COMMIT_MESSAGE}"
git push origin ${BRANCH}
