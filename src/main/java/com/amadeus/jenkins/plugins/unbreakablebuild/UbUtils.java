package com.amadeus.jenkins.plugins.unbreakablebuild;

import hudson.model.Failure;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.branch.Branch;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/*

 * Like in every project
 */
final class UbUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(UbUtils.class);

    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    private UbUtils() {
        // to prevent init
    }

    /**
     * Will log in Jenkins if possible
     */
    static void jenkinsLog(PrintStream logger, String message) {
        if (logger != null) {
            logger.append(message);
        }
    }

    static class BitbucketQueryResult {
        private int statusCode;
        private String message;
        private Exception exception;

        BitbucketQueryResult(int statusCode, String message, Exception exception) {
            this.statusCode = statusCode;
            this.message = message;
            this.exception = exception;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getMessage() {
            return message;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * Generates the payload to call the bitbucket APIs (ubValidate, ubFail)
     *
     * @param jobUrl         http://example.org/myjob
     * @param isolationZone  the isolation zone name
     * @param mergeRequestId the id current merge request (something like '1', '12', '154'...)
     * @param buildStatus    either SUCCESS of FAILURE
     * @return a byte array which is the expected payload
     */
    static String generateBitbucketPayload(
            String jobUrl, String isolationZone, String mergeRequestId, String buildStatus) {
        // NOSONAR
        //  {
        //    "jobUrl": "http://...",
        //    "isolationZone": "adas-dfd",
        //    "mergeRequestId": "1",
        //    "result": "FAILURE|SUCCESS"
        //  }
        Map<String, String> jsonInternal = new HashMap<>();
        jsonInternal.put("jobUrl", jobUrl);
        jsonInternal.put("isolationZone", isolationZone);
        jsonInternal.put("mergeRequestId", mergeRequestId);
        jsonInternal.put("result", buildStatus);
        return new JSONObject(jsonInternal).toString();
    }

    /**
     * Will send the notification to bitbucket
     *
     * @param url                         url on which the request must be sent
     * @param payload                     status:success/failure, others ...
     * @param usernamePasswordCredentials The credentials
     * @return A BitbucketQueryResult containing: status code, message, and exceptions
     */
    static BitbucketQueryResult sendBitbucketNotification(
            String url, String payload, UsernamePasswordCredentials usernamePasswordCredentials) {

        int returnCode = -1;
        String message = "! no messages !";
        Exception maybeException = null;
        String requestStringResult = "";

        String hostname;
        String protocol;
        int port;
        try {
            URL realUrl = new URL(url);
            hostname = realUrl.getHost();
            protocol = realUrl.getProtocol();
            port = realUrl.getPort();
        } catch (MalformedURLException e) {
            message = "The Bitbucket URL provided is not valid, aborting notification";
            return new BitbucketQueryResult(returnCode, message, e);
        }
        HttpHost httpHost = new HttpHost(hostname, port, protocol);


        org.apache.http.client.CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, usernamePasswordCredentials);

        // by default, apache http client does not do
        // preemptive auth: https://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d5e717
        // so we force it
        AuthCache authCache = new BasicAuthCache();
        authCache.put(httpHost, new BasicScheme());
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);

        try (CloseableHttpClient cli = HttpClientBuilder
                .create()
                .build()) {
            HttpPost post = new HttpPost(url);

            post.setHeader("Content-type", "application/json");

            // attach the payload
            post.setEntity(new StringEntity(payload, UTF8_CHARSET));

            // execute http call
            try (CloseableHttpResponse res = cli.execute(post, context)) {
                // save return code
                returnCode = res.getStatusLine().getStatusCode();

                // manage payload
                HttpEntity httpEntity = res.getEntity();
                try (InputStream inputStream = httpEntity.getContent()) {
                    requestStringResult = IOUtils.toString(inputStream, UTF8_CHARSET);
                }
                JSONObject obj = new JSONObject(requestStringResult);
                message = obj.getString("message");
            }
        } catch (IOException e) {
            LOGGER.error("Issue while notifying Bitbucket", e);
            maybeException = e;
        } catch (JSONException e) {
            LOGGER.error(String.format("Issue with the Bitbucket response payload: %s", requestStringResult), e);
            message = requestStringResult;
            maybeException = e;
        }
        return new BitbucketQueryResult(returnCode, message, maybeException);
    }

    /**
     * both implementations from Git And Bitbucket has their classes have the getCredentialsId method
     *
     * @param run jenkins run
     * @return an optional String
     */
    private static Optional<String> extractCredentialsIdFromRunScm(Run run) {
        if (!(run instanceof WorkflowRun)) {
            return Optional.empty();
        }

        WorkflowRun wfRun = (WorkflowRun) run;
        WorkflowJob job = wfRun.getParent();
        BranchJobProperty property = job.getProperty(BranchJobProperty.class);
        if (property == null) {
            return Optional.empty();
        }

        Branch branch = property.getBranch();
        ItemGroup<?> parent = job.getParent();
        if (!(parent instanceof WorkflowMultiBranchProject)) {
            return Optional.empty();
        }

        SCMSource scmSource = ((WorkflowMultiBranchProject) parent).getSCMSource(branch.getSourceId());
        if (scmSource == null) {
            return Optional.empty();
        }

        try {
            Method method =
                    scmSource.getClass().getMethod("getCredentialsId", (Class<?>[]) null);
            // method cannot be null
            String credentialsId = (String) method.invoke(scmSource);
            if (credentialsId != null) {
                return Optional.of(credentialsId);
            }
        } catch (
                NoSuchMethodException
                        | SecurityException
                        | IllegalAccessException
                        | IllegalArgumentException
                        | InvocationTargetException e) {
            LOGGER.error("We weren't able to get the getCredentialsId method "
                    + "from the scm found in the job", e);
        }
        return Optional.empty();
    }

    /**
     * @param run the jenkins run
     * @return the credentialsId that was used to perform the checkout scm
     */
    private static String extractCredentialsFromRunScmOrThrowFailure(Run run) {
        // it was said that the unbreakable build only works for multibranch pipelines
        // here we are trying to be smart by getting the SCM and the creds related
        Optional<String> credentialsId = UbUtils.extractCredentialsIdFromRunScm(run);

        if (!credentialsId.isPresent()) {
            String message = "UbreakableBuild - ABORTING - We were not able to determine the CredentialsId from "
                    + "the Run. Are you sure this is run comes from a MultiBranchPipelineJob ? ";
            throw new Failure(message);
        }
        return credentialsId.get();
    }


    private static com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials
    getJenkinsUsernamePasswordCredentials(Run<?, ?> run, String credentialId) {
        if (run == null) {
            return null;
        }
        com.cloudbees.plugins.credentials.common.StandardUsernameCredentials credentials =
                com.cloudbees.plugins.credentials.CredentialsProvider.findCredentialById(
                        credentialId,
                        com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
                        run);
        if (credentials instanceof com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials) {
            return (com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials) credentials;
        }
        return null;
    }

    /**
     * @param run the current run
     * @return the credentials to authenticate to bitbucket otherwise it make the build failed
     */
    static org.apache.http.auth.UsernamePasswordCredentials
    getBitbucketCredentialsOrFail(Run<?, ?> run, PrintStream jenkinsLogger) {
        // find which credentialId were used
        String credIdToUse = extractCredentialsFromRunScmOrThrowFailure(run);
        // ask jenkins the credentials corresponding to the credentialsId
        com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials creds =
                getJenkinsUsernamePasswordCredentials(run, credIdToUse);
        if (creds == null) {
            run.setResult(Result.FAILURE);
            String message = String.format("Unbreakable Build Error - Either the credential %s cannot be found "
                            + "or the user triggering the run is not permitted to use the credential in "
                            + "the context of the run, the build has been marked has failed.",
                    credIdToUse);
            jenkinsLog(jenkinsLogger, message);
            throw new Failure(message);
        }

        return new UsernamePasswordCredentials(creds.getUsername(), creds.getPassword().getPlainText());
    }

    /**
     * @param jobName the name of the job such
     * @return the isolationZone name
     */
    static String getIsolationZoneName(String jobName) {
        // example of jobName
        // SWB2/pipeline-unbreakable-build-plugin/UB%2Ftarget%2FbranchName%2FPR%2F12%2Ftry%2F1
        if (jobName != null) {
            String[] res = jobName.split("/");
            // ensure we have at least one '/' and take the string on the left
            if (res.length > 1 && StringUtils.isNotBlank(res[0])) {
                return res[0];
            }
        }
        return null;
    }
}
