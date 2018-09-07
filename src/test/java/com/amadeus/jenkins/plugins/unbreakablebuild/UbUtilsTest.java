package com.amadeus.jenkins.plugins.unbreakablebuild;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>
 * TestClass for UbUtils Class
 */
public class UbUtilsTest {

    @Test
    public void generateBitbucketPayload() throws JSONException {
        String payload = UbUtils.generateBitbucketPayload(
                "myJobUrl",
                "myIsolationZone",
                "myTargetBranchName",
                "SUCCESS");
        assertThat(payload).isNotNull();
        assertThat(payload).contains("myJobUrl");
        assertThat(payload).contains("myIsolationZone");
        assertThat(payload).contains("myTargetBranchName");
        assertThat(payload).contains("SUCCESS");
        new JSONObject(payload);
    }

    @Test
    public void getIsolationZoneName() {
        String validBranchName = "SWB2/pipeline-unbreakable-build-plugin/UB%2Ftarget%2FbranchName%2FPR%2F12%2Ftry%2F1";
        assertThat(UbUtils.getIsolationZoneName(validBranchName)).isEqualTo("SWB2");
    }

    @Test
    public void getIsolationZoneNameKo() {
        // return null because no string on left side of the '/'
        String invalidBranchName = "/pipeline-unbreakable-build-plugin/UB%2Ftarget%2FbranchName%2FPR%2F12%2Ftry%2F1";
        assertThat(UbUtils.getIsolationZoneName(invalidBranchName)).isNull();
    }

    @Test
    public void getIsolationZoneNameNull() {
        // return null when argument is null
        assertThat(UbUtils.getIsolationZoneName(null)).isNull();
    }

}