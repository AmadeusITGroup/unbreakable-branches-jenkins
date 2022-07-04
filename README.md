## Unbreakablebuild jenkins plugin

This plugin is a part of the plugin suite that brings the unbreakable build/branch feature to your Bitbucket project.

[![Build Status](https://travis-ci.com/AmadeusITGroup/unbreakable-branches-jenkins.svg?branch=master)](https://travis-ci.com/AmadeusITGroup/unbreakable-branches-jenkins)
[![codecov](https://codecov.io/gh/AmadeusITGroup/unbreakable-branches-jenkins/branch/master/graph/badge.svg)](https://codecov.io/gh/AmadeusITGroup/unbreakable-branches-jenkins)

### The problem the Unbreakable build/branch tries to solve:

The normal workflow is to open a pull-request (PR) which, in turn, triggers a build. The build succeeds, then the owner of the repository
clicks on merge, and another build is triggered. And sometimes the build fails, which leads to a broken target
branch.

It can be broken for multiple reasons:

- another PR was merged on the target in-between
- an environment issue
- lenient dependency declaration got another version which lead to a build break
- others.

### What does this plugin do:

If the Jenkins job is eligible to unbreakable build (by having environment variables such as UB_BRANCH_REF) then
at the end of the build a notification to Bitbucket is sent according to the build status.

The notification can also be sent manually through two verbs: `ubValidate` or `ubFail`.

#### Notable difference with the stashnotifier-plugin

The stashplugin reports indeed a status on a commit, but for the unbreakable build a different API is dedicated on Bitbucket.

### What is done on the Bitbucket side:

The GIT HEAD of the target branch is moved on top of the code validated during the PR.
The target branch can thus always have a successful build status.

Of course some restrictions are put in places on Bitbucket side and on Jenkins side, such as:

- There is no `merge` button, but a `merge request` button that will queue the build. The merge will happen automatically at the end of the build if the build succeeds
- direct push on the branch is forbidden
- the Merge requests on different PRs will process the builds sequentially

Those restrictions are setup by Bitbucket once you activate the unbreakable build on a branch for your repository.

### Prerequisites to run the code locally:
- Maven (tested agains 3.5)
- Git should be installed (otherwise there is this `java.io.IOException: Cannot run program "git": java.io.IOException:`
in the tests)

### Prerequisites to run in Jenkins:
- The Bitbucket you are running against should have the UnbreakableBranch plugin installed.
- The bitbucketBranch source plugin (Jenkins plugin) should be a patched so that
 mandatory environment variables are injected. Note that this plugin hasn't been released yet.

## Update the dependencies

```shell
# https://www.jenkins.io/doc/developer/plugin-development/dependency-management/
mvn versions:update-parent
mvn clean verify

```

