package com.amadeus.jenkins.plugins.unbreakablebuild;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>
 * aims to test the POJO regex
 */
public class UbBranchPojoTest {

    static final String VALID_UB_BRANCH_NAME = UbBranchPojo.UB_BRANCH_PREFIX + "1";

    private static final String INVALID_UB_BRANCH_NAME = UbBranchPojo.UB_BRANCH_PREFIX + "???";

    @Test
    public void fromUbBranchName() {
        UbBranchPojo result =
                UbBranchPojo.fromUbBranchName(VALID_UB_BRANCH_NAME);
        assertThat(result).isNotNull();
        assertThat(result.getTargetBranchName()).isEqualTo("refs/ubuilds/1");
        assertThat(result.getMergeRequestId()).isEqualTo("1");
    }

    @Test
    public void fromInvalidUbBranchName() {
        UbBranchPojo result =
                UbBranchPojo.fromUbBranchName(INVALID_UB_BRANCH_NAME);
        assertThat(result).isNull();
    }

    @Test
    public void getMergeRequestIdFromUbBranchName() {
        String result =
                UbBranchPojo.getMergeRequestIdFromUbBranchName(VALID_UB_BRANCH_NAME);
        assertThat(result).isEqualTo("1");
    }

    @Test
    public void getMergeRequestIdFromINVALIDEUbBranchName() {
        String result =
                UbBranchPojo.getMergeRequestIdFromUbBranchName(INVALID_UB_BRANCH_NAME);
        assertThat(result).isNull();
    }
}