package com.amadeus.jenkins.plugins.unbreakablebuild;


import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

/**
 * <p>
 * This method declares the ubValidate method callable from the Jenkinsfile
 */

public class UbValidateStep extends Step {

    static final String UB_ACTION_NAME = "ubValidate";

    @DataBoundConstructor
    public UbValidateStep() {
        // jenkins will use this constructor
    }

    @Override
    public StepExecution start(StepContext stepContext) {
        return new Execution(stepContext);
    }

    public static class Execution extends UbExecution {

        Execution(StepContext context) {
            super(context, UB_ACTION_NAME, UbJenkinsEnvBean.Status.SUCCESS);
        }

        private static final long serialVersionUID = 1L;
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return UB_ACTION_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Unbreakable Build validate";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class, EnvVars.class);
        }
    }
}