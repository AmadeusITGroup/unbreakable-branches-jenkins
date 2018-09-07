package com.amadeus.jenkins.plugins.unbreakablebuild;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.scm.api.SCMRevisionAction;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

@Extension
public class UbBuildListener extends RunListener<Run<?, ?>> {

    /**
     * This method is called once the job is completed. At that moment we will
     * try to identify whether it was an unbreakable build and if so, we figure
     * out whether it called an Unbreakable verb (such as ubValidate, or
     * ubFail), and we finally call the verb if it hasn't been called.
     *
     * @param run      the current run/build
     * @param listener the task listener attached to the build
     */
    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {

        SCMRevisionAction scmRevisionAction = run.getAction(SCMRevisionAction.class);

        if (scmRevisionAction == null) {
            // no scm revision --> not a multi-branch pipeline -> not UB ->
            // early exit
            return;
        }
        PrintStream jenkinsLogger = listener.getLogger();
        try {
            EnvVars environment = run.getEnvironment(listener);

            // if not null -> unbreakable build run
            if (!UbJenkinsEnvBean.isUnbreakableBuild(environment)) {
                // the branch is not an unbreakable build -> early exit
                return;
            }

            // if ubFail or ubValidate has NOT been called then this ubAction is null
            if (run.getAction(UbAction.class) != null) {
                // an ubAction has been already called -> normal use case -> exit
                return;
            }

            // -- at this point, ubFail or ubValidate has NOT been called and MUST be called --


            // trying to get the plugin credentials, because they are needed
            // this one will throw an exception nothing matches the IZ_USER credentials
            org.apache.http.auth.UsernamePasswordCredentials credentials =
                    UbUtils.getBitbucketCredentialsOrFail(run, jenkinsLogger);

            // find the current status of the build and call the proper method
            Result result = run.getResult();

            UbUtils.jenkinsLog(jenkinsLogger, "\n Unbreakable build: unbreakable verb "
                    + "hasn't been called. We will call it with current build status: " + result + "\n");

            // Get Associated status
            String actionName;
            UbJenkinsEnvBean.Status statusEnum;
            if (Result.SUCCESS.equals(result)) {
                statusEnum = UbJenkinsEnvBean.Status.SUCCESS;
                actionName = UbValidateStep.UB_ACTION_NAME;
            } else {
                statusEnum = UbJenkinsEnvBean.Status.FAILURE;
                actionName = UbFailStep.UB_ACTION_NAME;
            }

            // Call Bitbucket
            UbJenkinsEnvBean.collectEnvAndNotifyBitbucket(actionName, environment,
                    jenkinsLogger, statusEnum, credentials, run);

        } catch (IOException | InterruptedException e) {
            run.setResult(Result.FAILURE);
            UbUtils.jenkinsLog(jenkinsLogger, "\n -- UbBuildListener --\n"
                    + "The following exception occurred while trying to "
                    + "get the UB_ACTION_CALLED environment variable" + "\n");
            throw new UbTechnicalException("Occurred while notifying Bitbucket (unbreakableBuild)", e);
        }
    }

}
