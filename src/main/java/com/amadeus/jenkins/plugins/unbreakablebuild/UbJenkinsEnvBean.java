package com.amadeus.jenkins.plugins.unbreakablebuild;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Failure;
import hudson.model.Result;
import hudson.model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * <p>
 * This object contains the minimal information needed for the unbreakable build
 */
public class UbJenkinsEnvBean {

    static final String BITBUCKET_API_VERSION = "1.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(UbJenkinsEnvBean.class);

    private static final int HTTP_CODE_LOWER_RANGE_OK = 200;
    private static final int HTTP_CODE_UPPER_RANGE_KO = 300;

    private String bitbucketUrl;
    private String projectKey;
    private String repositorySlug;
    private String refSpec;
    private String commitId;
    private String jobUrl;
    private String isolationZone;

    /**
     * Build status
     */
    enum Status {
        SUCCESS, FAILURE
    }

    /**
     * Mandatory environment variables so that the unbreakablebuild works
     */
    enum EnvVarName {
        SCM_URL, BITBUCKET_PROJECT, BITBUCKET_REPOSITORY, UNBREAKABLE_REFSPEC, COMMIT, JOB_DISPLAY_URL, JOB_NAME,
    }

    /**
     * This constructor will attempt to initialize a UbJenkinsEnvBean with all the
     * environment variables.
     * <p>
     * If one of the env variable is missing, it will fail with
     * a @{hudson.model.Failure}
     *
     * @param envVars the environment variables available in the Run
     */
    UbJenkinsEnvBean(EnvVars envVars, Run<?, ?> run) {
        List<String> missingEnv = Arrays.stream(EnvVarName.values())
                .filter(name -> !envVars.containsKey(name.toString()))
                .map(Enum::toString)
                .collect(Collectors.toList());

        if (!missingEnv.isEmpty()) {
            run.setResult(Result.FAILURE);
            String message = "These environment variables are missing "
                    + String.join(", ", missingEnv) + " and are needed for the unbreakable "
                    + "build feature. Please, make sure that your project-repository is registered as an "
                    + "Unbreakable build in Bitbucket, and make sure that your instance of the SWB pipeline"
                    + " is correctly configured, the build has been marked has failed.";
            throw new Failure(message);
        }

        // this is from the BB plugin (added by SWB change)
        bitbucketUrl = envVars.get(EnvVarName.SCM_URL.toString());
        projectKey = envVars.get(EnvVarName.BITBUCKET_PROJECT.toString());
        repositorySlug = envVars.get(EnvVarName.BITBUCKET_REPOSITORY.toString());

        // looks like this : refs/ubuilds/9999
        refSpec = envVars.get(EnvVarName.UNBREAKABLE_REFSPEC.toString());

        // this is the commit from unbreakable build (already Fast Forward
        // Merge)
        // From my understanding this code is called from special branch which
        // is the actual merge
        // So, the commit_id should be the one to use
        // and this commit id is coming from the BB plugin (added by SWB change)
        // --> from the BitbucketSCMSourceBranch plugin
        commitId = envVars.get(EnvVarName.COMMIT.toString());

        // --> from the display-url-api plugin
        jobUrl = envVars.get(EnvVarName.JOB_DISPLAY_URL.toString());

        // the isolation correspond to the parent project
        isolationZone = UbUtils.getIsolationZoneName(envVars.get(EnvVarName.JOB_NAME.toString()));
    }


    /**
     * This method will contact bitbucket to notify whether it should merge or
     * mark the build result as failed
     *
     * @param status      SUCCEED or FAILURE
     * @param logger      the Jenkins logger to print in the jenkins console
     * @param credentials bitbucket credentials to use UB verbs
     * @throws AbortException Will be thrown if the branchName of the UbJenkinsEnvBean is not
     *                        matching the convention
     */
    void notifyBitbucket(Status status,
                         PrintStream logger,
                         org.apache.http.auth.UsernamePasswordCredentials credentials,
                         Run<?, ?> run)
            throws AbortException {

        UbUtils.jenkinsLog(logger, "\n ----- UNBREAKABLE BUILD VERB CALLED ----- \n");

        UbBranchPojo ubPojo = UbBranchPojo.fromUbBranchName(refSpec);
        if (ubPojo == null) {
            throw new AbortException(
                    "Unbreakable build actions should only be called from "
                            + "Unbreakable Build branches (name should match: '"
                            + UbBranchPojo.UB_BRANCH_REGEX + "'. Current branch refSpec is '" + refSpec + "'");
        }

        String mergeRequestId = ubPojo.getMergeRequestId();

        String url = generateBitbucketUrl();

        UbUtils.jenkinsLog(logger, "calling URL: " + url);

        // build payload
        String payload = UbUtils.generateBitbucketPayload(jobUrl, isolationZone, mergeRequestId, status.name());
        UbUtils.jenkinsLog(logger, "With Payload: \n" + payload);

        // query Bitbucket
        UbUtils.BitbucketQueryResult bbResult = UbUtils.sendBitbucketNotification(url, payload, credentials);

        // analyze the results --> will throw Failure if not OK
        analyseBbQueryResult(bbResult, status, logger, run);

        // finally manage the failure of the build
        if (Status.FAILURE.equals(status)) {
            run.setResult(Result.FAILURE);
            throw new Failure("Unbreakable build marks the build as failed");
        }
    }

    /**
     * Put business logic of handling the bitbucket response
     *
     * @param bbResult    result of the notification
     * @param buildStatus status of the build
     * @param logger      Jenkins logger
     */
    private static void analyseBbQueryResult(
            UbUtils.BitbucketQueryResult bbResult, Status buildStatus, PrintStream logger, Run<?, ?> run) {
        // read results
        int statusCode = bbResult.getStatusCode();
        String message = bbResult.getMessage();
        LOGGER.trace("status Code: {}", statusCode);

        boolean isHttpCodeInRangeOk = statusCode >= HTTP_CODE_LOWER_RANGE_OK && statusCode < HTTP_CODE_UPPER_RANGE_KO;

        // SUCCESS CASE
        if (isHttpCodeInRangeOk && bbResult.getException() == null) {
            UbUtils.jenkinsLog(logger, String.format(
                    "Notification of UB buildStatus: '%s' sent successfully to bitbucket, "
                            + "status code: %d, message: '%s'", buildStatus, statusCode, message));

            UbUtils.jenkinsLog(logger, "\n ----- UNBREAKABLE BUILD FINISHED ----- \n");
        } else if (isHttpCodeInRangeOk) {
            UbUtils.jenkinsLog(logger, String.format(
                    "Notification of UB buildStatus: '%s' sent successfully to bitbucket but with Exception. "
                            + "Status code: %d, message: '%s', exception: %s",
                    buildStatus, statusCode, message, bbResult.getException()));

            UbUtils.jenkinsLog(logger, "\n ----- UNBREAKABLE BUILD FINISHED ----- \n");
        } else {
            run.setResult(Result.FAILURE);
            String errorMessage = String.format(
                    "The notification to bitbucket went wrong, build marked as failed. "
                            + "Status code: %d, message: %s, exception: %s",
                    statusCode, message, bbResult.getException());
            LOGGER.error(errorMessage, bbResult.getException());
            UbUtils.jenkinsLog(logger, "\n" + errorMessage + "\n");
            throw new Failure(errorMessage);
        }
    }


    private String generateBitbucketUrl() {
        // POST
        // http://example.org/rest/ubuild/1.0/projects/{projectKey}/repos/
        // {repositorySlug}/pull-requests/{pullRequestId}/commits/{commitId}/notify
        return String.format(
                "%s/rest/ubuild/%s/projects/%s/repos/%s/commits/%s/notify",
                bitbucketUrl, BITBUCKET_API_VERSION, projectKey, repositorySlug, commitId);
    }

    /**
     * Core function of the unbreakable build.
     *
     * @param actionName  ubValidate, or ubFail (only logging purposes)
     * @param envVars     environment variables provided by jenkins during the run
     * @param logger      jenkins logger (provided during the run)
     * @param status      either passed or failed
     * @param credentials the credentialsId to use ?
     * @param run
     * @throws AbortException
     */
    static void collectEnvAndNotifyBitbucket(String actionName,
                                             EnvVars envVars,
                                             PrintStream logger,
                                             UbJenkinsEnvBean.Status status,
                                             org.apache.http.auth.UsernamePasswordCredentials credentials,
                                             Run<?, ?> run) throws AbortException {
        LOGGER.info("{} - Collecting environment variables", actionName);

        UbJenkinsEnvBean ubJenkinsEnvBean = new UbJenkinsEnvBean(envVars, run);
        ubJenkinsEnvBean.notifyBitbucket(status, logger, credentials, run);

        LOGGER.info("{} - execution finished", actionName);
    }

    /**
     * Will return true in case of in unbreakable build context.
     *
     * @param envVars Environment variables map.
     * @return true in case of envVars.get(EnvVarName.UNBREAKABLE_REFSPEC) not null
     */
    static boolean isUnbreakableBuild(EnvVars envVars) {
        return envVars.containsKey(EnvVarName.UNBREAKABLE_REFSPEC.toString());
    }
}
