package com.amadeus.jenkins.plugins.unbreakablebuild;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.model.Failure;
import hudson.model.Run;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class UbJenkinsEnvBeanTest {

    /* CONSTANTS */
    private static final String USER = "git-user";
    private static final String PASSWORD = "git-secret";


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    private EnvVars envVars = new EnvVars();

    private UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(USER, PASSWORD);

    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private Run run;

    private void setUpEnvForUbHelper() {
        envVars.put("SCM_URL", "http://localhost:" + wireMockRule.port());
        envVars.put("BITBUCKET_PROJECT", "myProject");
        envVars.put("BITBUCKET_REPOSITORY", "myRepo");
        envVars.put("UNBREAKABLE_REFSPEC", "refs/ubuilds/9999");
        envVars.put("BRANCH_NAME", UbBranchPojoTest.VALID_UB_BRANCH_NAME);
        envVars.put("COMMIT", "1aed25f357e");
        envVars.put("JOB_DISPLAY_URL", "http:// example.org");
        envVars.put("JOB_NAME", "SWB2/pipeline-unbreakable-build-plugin"
                + "/UB%2Ftarget%2FbranchName%2FPR%2F12%2Ftry%2F1");
    }


    private static void checkNotificationReceived(UbJenkinsEnvBean.Status status) {
        MockBitbucketHelper.checkNotificationReceived(status, USER, PASSWORD);
    }

    @Test(expected = Failure.class)
    public void constructorFails() {
        // it will fail because environment setup is missing
        new UbJenkinsEnvBean(envVars, run);
    }


    @Test
    public void constructorOk() {
        setUpEnvForUbHelper();
        new UbJenkinsEnvBean(envVars, run);
    }

    private void stubBitbucketServer(int statusCode, String message) {
        setUpEnvForUbHelper();
        MockBitbucketHelper.stubNotificationWith(wireMockRule, statusCode, message);
    }

    private UbJenkinsEnvBean newBean() {
        return new UbJenkinsEnvBean(envVars, run);
    }

    @Test
    public void notifyBitbucketOk() throws Exception {
        stubBitbucketServer(200, "OK");

        UbJenkinsEnvBean ubJenkinsEnvBean = new UbJenkinsEnvBean(envVars, run);

        UbJenkinsEnvBean.Status status = UbJenkinsEnvBean.Status.SUCCESS;
        ubJenkinsEnvBean.notifyBitbucket(
                status, null, credentials, run);

        checkNotificationReceived(status);
    }

    @Test
    public void notifyBitbucketOkNoBody() throws Exception {
        // sometimes no body is returned by bitbucket
        // nevertheless if the status code is 200 we should consider it OK

        stubBitbucketServer(200, "");

        UbJenkinsEnvBean ubJenkinsEnvBean = new UbJenkinsEnvBean(envVars, run);

        UbJenkinsEnvBean.Status status = UbJenkinsEnvBean.Status.SUCCESS;
        ubJenkinsEnvBean.notifyBitbucket(
                status, null, credentials, run);

        checkNotificationReceived(status);
    }

    @Test
    public void notifyBitbucketOkStatusfFailure() {
        stubBitbucketServer(200, "OK");
        UbJenkinsEnvBean ubJenkinsEnvBean = newBean();

        // build is marked as failed
        assertThatExceptionOfType(Failure.class).isThrownBy(
                () -> ubJenkinsEnvBean.notifyBitbucket(UbJenkinsEnvBean.Status.FAILURE, null, credentials, run))
                .withMessage("Unbreakable build marks the build as failed");

        // checking that bitbucket got the failure notification
        checkNotificationReceived(UbJenkinsEnvBean.Status.FAILURE);

    }

    @Test
    public void notifyBitbucketKoBecauseBadURL() {
        stubBitbucketServer(200, "OK");
        envVars.put("SCM_URL", "ssh://git@example.com");
        UbJenkinsEnvBean ubJenkinsEnvBean = newBean();

        assertThatExceptionOfType(Failure.class).isThrownBy(
                () -> ubJenkinsEnvBean.notifyBitbucket(UbJenkinsEnvBean.Status.SUCCESS, null, credentials, run))
                .withMessage("The notification to bitbucket went wrong, build marked as failed. "
                        + "Status code: -1, message: The Bitbucket URL provided is not valid, aborting notification, "
                        + "exception: java.net.MalformedURLException: unknown protocol: ssh");

    }

    @Test
    public void failToNotifyBitbucket() {
        stubBitbucketServer(400, "");
        UbJenkinsEnvBean ubJenkinsEnvBean = newBean();

        UbJenkinsEnvBean.Status status = UbJenkinsEnvBean.Status.SUCCESS;
        assertThatExceptionOfType(Failure.class)
                .isThrownBy(() -> ubJenkinsEnvBean.notifyBitbucket(status, null, credentials, run))
                .withMessage("The notification to bitbucket went wrong, build marked as failed. Status code: 400, "
                        + "message: { \"message\":  }, exception: org.json.JSONException: "
                        + "Missing value at character 14");
    }

    @Test
    public void failToNotifyBitbucketWithBody() {
        stubBitbucketServer(404, "Wrong URL");
        UbJenkinsEnvBean ubJenkinsEnvBean = newBean();

        UbJenkinsEnvBean.Status status = UbJenkinsEnvBean.Status.SUCCESS;
        assertThatExceptionOfType(Failure.class)
                .isThrownBy(() -> ubJenkinsEnvBean.notifyBitbucket(status, null, credentials, run))
                .withMessage(
                        "The notification to bitbucket went wrong, build marked as failed. "
                                + "Status code: 404, message: Wrong URL, exception: null");
    }

    @Test
    public void testInvalidRefSpec() {
        setUpEnvForUbHelper();
        envVars.put("UNBREAKABLE_REFSPEC", "InvalidRefSpec");

        // new bean is instanciated, but has invalid ref spec
        UbJenkinsEnvBean ubJenkinsEnvBean = newBean();

        assertThatExceptionOfType(AbortException.class)
                .isThrownBy(() ->
                        ubJenkinsEnvBean.notifyBitbucket(UbJenkinsEnvBean.Status.SUCCESS, null, credentials, run))
                .withMessage(
                        "Unbreakable build actions should only be called from Unbreakable "
                                + "Build branches (name should match: 'refs/ubuilds/(?<mergeRequestId>[0-9]+)'. "
                                + "Current branch refSpec is 'InvalidRefSpec'");

    }
}