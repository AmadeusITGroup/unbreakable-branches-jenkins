package com.amadeus.jenkins.plugins.unbreakablebuild;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import com.amadeus.jenkins.plugins.unbreakablebuild.UbJenkinsEnvBean.EnvVarName;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import hudson.EnvVars;
import hudson.model.EnvironmentContributor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.RemoteNameSCMSourceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;


public class UbStepTest {

    /* CONSTANTS */
    private static final String CREDS_ID = "IZ_USER";
    private static final String PROJECT = "project";
    private static final String REPOSITORY = "repository";
    private static final String USER = "git-user";
    private static final String PASSWORD = "git-secret";


    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static UsernamePasswordCredentials creds;

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();


    @BeforeClass
    public static void setUpBeforeClass() {
        creds = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                CREDS_ID,
                null,
                USER,
                PASSWORD);
    }


    @Before
    public void setUp() {
        bitbucketScmEnvContributor.port = wireMockRule.port();
        MockBitbucketHelper.stubNotificationWith(wireMockRule);
    }


    /* --------------------------- TEST UTILS -------------------------------------*/

    private static void checkNotificationReceived(UbJenkinsEnvBean.Status status) {
        MockBitbucketHelper.checkNotificationReceived(status, USER, PASSWORD);
    }

    private WorkflowMultiBranchProject createMultiBranchProject() throws Exception {
        return j.jenkins.createProject(WorkflowMultiBranchProject.class, PROJECT);
    }

    private void createSampleGitRepo(String jenkinsfile) throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", jenkinsfile);
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
    }

    private void attachSampleRepoToProject(WorkflowMultiBranchProject mp) {
        GitSCMSource gitScmSource = new GitSCMSource(sampleRepo.toString());
        gitScmSource.setCredentialsId(CREDS_ID);

        List<SCMSourceTrait> traits = new ArrayList<>();
        traits.add(new BranchDiscoveryTrait());
        traits.add(new WildcardSCMHeadFilterTrait("*", ""));
        traits.add(new RemoteNameSCMSourceTrait("origin"));
        gitScmSource.setTraits(traits);

        BranchSource branchSource = new BranchSource(gitScmSource);
        branchSource.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[0]));
        mp.getSourcesList().add(branchSource);

        // verify that they were added
        for (SCMSource source : mp.getSCMSources()) {
            assertThat(mp).isEqualTo(source.getOwner());
        }
    }


    private void addCredz() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(Collections.singletonMap(
                Domain.global(),
                Collections.singletonList(creds)
        ));
    }

    private static @Nonnull
    WorkflowJob scheduleAndFindBranchProject(
            @Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    private static @Nonnull
    WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) {
        WorkflowJob p = mp.getItem(name);
        assertThat(p).as(name + "project not found").isNotNull();
        return p;
    }


    /* --------------------------- THE TESTS -------------------------------------*/

    @Test
    public void testComplainIfNoCreds() throws Exception {
        // Credentials not added --> should fail
        WorkflowMultiBranchProject mp = createMultiBranchProject();
        createSampleGitRepo("ubValidate()");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();

        WorkflowRun r = j.assertBuildStatus(Result.FAILURE, b1);
        assertThat(JenkinsRule.getLog(r)).contains(
                "Unbreakable Build Error - Either the credential IZ_USER cannot be found");
    }


    @Test
    public void testComplainIfNoEnvionmentVariables() throws Exception {
        addCredz();
        WorkflowMultiBranchProject mp = createMultiBranchProject();
        createSampleGitRepo("ubValidate()");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();

        WorkflowRun r = j.assertBuildStatus(Result.FAILURE, b1);

        assertThat(JenkinsRule.getLog(r)).contains(
                " These environment variables are missing ");
        assertThat(JenkinsRule.getLog(r)).contains(
                " and are needed for the unbreakable build feature. "
                        + "Please, make sure that your project-repository is "
                        + "registered as an Unbreakable build in Bitbucket, "
                        + "and make sure that your instance of the SWB pipeline "
                        + "is correctly configured, the build has been marked has failed.");
    }

    @Test
    public void testComplainNotMultibranchPipeline() throws Exception {
        // adding credentials so that it does not block on that
        addCredz();
        WorkflowJob p = j.createProject(WorkflowJob.class, PROJECT);

        // but it will fail as as this is not a multibranch pipeline project
        p.setDefinition(new CpsFlowDefinition("ubValidate()", true));

        WorkflowRun r = j.assertBuildStatus(Result.FAILURE, Objects.requireNonNull(p.scheduleBuild2(0)));
        assertThat(JenkinsRule.getLog(r)).contains(
                "UbreakableBuild - ABORTING - We were not able to determine the CredentialsId from the Run");
        assertThat(JenkinsRule.getLog(r)).contains(
                "Are you sure this is run comes from a MultiBranchPipelineJob ?");

    }


    /**
     * We manually call the ubValidate verb, and checkNotificationReceived
     * - that the build is passed
     * - that the http call to BB was done with the "SUCCESS" status
     * - that the UbAction was added to the build
     */
    @Test
    public void unbreakableBuildPassed() throws Exception {
        addCredz();
        WorkflowMultiBranchProject mp = createMultiBranchProject();

        createSampleGitRepo("ubValidate()");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();

        // job should be passed alright
        j.assertBuildStatusSuccess(b1);

        // unbreakable build has been called and it notified successfully bitbucket
        String expectedUbSuccess = "Notification of UB buildStatus: 'SUCCESS'";
        j.assertLogContains(expectedUbSuccess, b1);

        // checkNotificationReceived that the mock received successfully the call
        checkNotificationReceived(UbJenkinsEnvBean.Status.SUCCESS);

        // checkNotificationReceived that since the UbreakableBuild ran, then the UbAction was added
        UbAction ubAction = b1.getAction(UbAction.class);
        assertThat(ubAction).isNotNull();

        // for the pure sake of code coverage we will also get the values of the action :)
        assertThat(ubAction.getDisplayName()).isEqualTo("Unbreakable Build Action");
        assertThat(ubAction.getIconFileName()).isNull();
        assertThat(ubAction.getUrlName()).isNull();
    }


    /**
     * We manually call the ubFail verb, and checkNotificationReceived
     * - that the build is maked as failed
     * - that the http call to BB was done with the "FAILED" status
     * - that the UbAction was added to the build
     */
    @Test
    public void unbreakableBuildFailed() throws Exception {
        addCredz();
        WorkflowMultiBranchProject mp = createMultiBranchProject();
        createSampleGitRepo("ubFail()");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();

        // job should be marked as failed by the unbreakable build
        j.assertBuildStatus(Result.FAILURE, b1);

        // unbreakable build has been called and it notified successfully bitbucket
        String expectedUbSuccess = "Notification of UB buildStatus: 'FAILURE'";
        j.assertLogContains(expectedUbSuccess, b1);

        // checkNotificationReceived that the mock received successfully the call
        checkNotificationReceived(UbJenkinsEnvBean.Status.FAILURE);

        // checkNotificationReceived that since the UbreakableBuild ran, then the UbAction was added
        UbAction ubAction = b1.getAction(UbAction.class);
        assertThat(ubAction).isNotNull();
    }


    /**
     * We don't call manually the unbreakableBuild verb, but we
     * checkNotificationReceived that
     * - that the build is passed
     * - the buildListener detected that it was an unbreakable build, and called the verb
     * - that the http call to BB was done with the "SUCCESS" status
     * - that the UbAction was added to the build
     */
    @Test
    public void unbreakableBuildListenerCallsUbValidate() throws Exception {
        addCredz();
        WorkflowMultiBranchProject mp = createMultiBranchProject();
        createSampleGitRepo("def iSay = 'hi'");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();


        // job should be passed alright
        j.assertBuildStatusSuccess(b1);

        // now as it has what it requires to be considered as an unbreakable build project
        // the UB verbs (ubValidate/ubFail) should be called at the end of the execution

        // unbreakable build has been called and it notified successfully bitbucket
        String expectedUbSuccess = "Notification of UB buildStatus: 'SUCCESS'";
        j.assertLogContains(expectedUbSuccess, b1);

        // checkNotificationReceived that the mock received successfully the call
        checkNotificationReceived(UbJenkinsEnvBean.Status.SUCCESS);
    }


    /**
     * We don't call manually the unbreakableBuild verb, but we
     * checkNotificationReceived that
     * - that the build is passed
     * - the buildListener detected that it was an unbreakable build, and called the verb
     * - that the http call to BB was done with the "SUCCESS" status
     * - that the UbAction was added to the build
     */
    @Test
    public void unbreakableBuildListenerCallsUbFail() throws Exception {
        addCredz();
        WorkflowMultiBranchProject mp = createMultiBranchProject();
        createSampleGitRepo("fail");
        attachSampleRepoToProject(mp);

        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        // checkNotificationReceived that one job was triggered
        assertThat(1).isEqualTo(mp.getItems().size());
        j.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();


        // job should be failed (we call explicitely fail)
        j.assertBuildStatus(Result.FAILURE, b1);

        // now as it has what it requires to be considered as an unbreakable build project
        // the UB verbs (ubValidate/ubFail) should be called at the end of the execution

        // unbreakable build has been called and it notified successfully bitbucket
        String expectedUbSuccess = "Notification of UB buildStatus: 'FAILURE'";
        j.assertLogContains(expectedUbSuccess, b1);

        // checkNotificationReceived that the mock received successfully the call
        checkNotificationReceived(UbJenkinsEnvBean.Status.FAILURE);
    }

    /**
     * In order to have the unbreakableBuild work there is a dependency
     * on the a patched Bitbucket Branch Source Plugin.
     * The plugin adds environments variables to the build.
     */
    @TestExtension({
            "unbreakableBuildFailed",
            "unbreakableBuildPassed",
            "unbreakableBuildListenerCallsUbValidate",
            "unbreakableBuildListenerCallsUbFail",
            "testComplainIfNoCreds",
            "testComplainNotMultibranchPipeline"
    })
    public static final BitbucketScmEnvContributor bitbucketScmEnvContributor = new BitbucketScmEnvContributor();

    public static class BitbucketScmEnvContributor extends EnvironmentContributor {
        int port;

        @Override
        public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
            // target our wireMock instead of bitbucket.org
            envs.put(EnvVarName.SCM_URL.toString(), "http://localhost:" + port);

            envs.put(EnvVarName.UNBREAKABLE_REFSPEC.toString(), "refs/ubuilds/22");

            envs.put(EnvVarName.BITBUCKET_PROJECT.toString(), PROJECT);
            envs.put(EnvVarName.BITBUCKET_REPOSITORY.toString(), REPOSITORY);

            envs.put(EnvVarName.COMMIT.toString(), "e2fef687f72cea001fd856bded022449fbf1a885");
        }
    }

}