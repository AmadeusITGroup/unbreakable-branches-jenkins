package com.amadeus.jenkins.plugins.unbreakablebuild;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import javax.annotation.Nonnull;
import java.io.PrintStream;

/**
 * <p>
 * This is the abstract execution class the ubFail and ubValidate inherit from
 */
public abstract class UbExecution extends SynchronousStepExecution<Void> {

    private static final long serialVersionUID = 7038940543914178843L;

    private String actionName;
    private UbJenkinsEnvBean.Status status;

    protected UbExecution(@Nonnull StepContext context, String actionName, UbJenkinsEnvBean.Status status) {
        super(context);
        this.actionName = actionName;
        this.status = status;
    }

    /**
     * Meat of the execution.
     * <p>
     * When this method returns, a step execution is over.
     */
    @Override
    protected Void run() throws Exception {
        StepContext context = getContext();
        TaskListener taskListener = context.get(TaskListener.class);
        PrintStream logger = null;
        if (taskListener != null) {
            logger = taskListener.getLogger();
        }

        EnvVars envVars = context.get(EnvVars.class);
        Run<?, ?> run = context.get(Run.class);


        // it was said that the unbreakable build only works for multibranch pipelines
        // here we are trying to be smart by getting the SCM and the creds related
        UsernamePasswordCredentials usernamePasswordCredentials = UbUtils.getBitbucketCredentialsOrFail(run, logger);

        // Register the ubAction in the run
        // reason: so we know that an execution (ubValidate/ubFail) has been called
        run.addAction(new UbAction());

        // core of the step
        UbJenkinsEnvBean.collectEnvAndNotifyBitbucket(
                actionName,
                envVars,
                logger,
                status,
                usernamePasswordCredentials,
                run);
        return null;
    }


}
