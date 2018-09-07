## Unbreakablebuild jenkins plugin

This plugin is a part of the plugin suite that brings the unbreakable build/branch feature to your bitbucket project.

### The problem the Unbreakable build/branch tries to solve:

The normal workflow is, open a PR which triggers a build. The build is passed, then the owner of the repository
clicks on merge, and another build is triggered. And sometimes the build is failed, which leads to a target
branch that is broken.

It can be broken for multiple reasons:

- another PR was merged on the target before
- an environment issue
- lenient dependency declaration got another version which lead to a build break
- others.

### What does this plugin do:

If the jenkins job is eligible to unbreakable build (by having environment variables such as UB_BRANCH_REF) then
at the end of the build a notification to bitbucket is sent according to the build status.

The notification can also be sent manually through two verbs: `ubValidate` or `ubFail`.

#### Notable difference with the stashnotifier-plugin

The stashplugin reports indeed a status on a commit, but for the unbreakable build a different API is dedicated on Bitbucket.

### What is done on the bitbucket side:

The GIT HEAD of the target branch is moved on top of the code validated during the PR.
The target branch can thus always have a successful build status.

Of course some restrictions restrictions are put in places on bitbucket side and on jenkins side, such as:

- There is no `merge` button, but a `merge request` button that will queue the build. The merge will happen automatically at the end of the build if the build is passed
- direct push on the branch is forbidden
- the Merge requests on different PRs will process the builds sequentially

Those restrictions are setup while by bitbucket once you activate the unbreakable build on a branch for your repository.

### Prerequisites to run the code locally:
- Maven (tested agains 3.5)
- Git should be installed (otherwise there is this `java.io.IOException: Cannot run program "git": java.io.IOException:`
in the tests)

### Prerequisites to run in Jenkins:
- The bitbucket you are running against should have the UnbreakableBranch plugin installed.
- The bitbucketBranch source plugin (jenkins plugin) should be a patched so that
 mandatory environment variables are injected. Note that this plugin hasn't been released yet.
