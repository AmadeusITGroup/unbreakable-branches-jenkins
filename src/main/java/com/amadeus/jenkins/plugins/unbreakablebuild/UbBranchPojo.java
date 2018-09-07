package com.amadeus.jenkins.plugins.unbreakablebuild;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * used to transform the unbreakablebuild branchName into a Pojo
 */
public final class UbBranchPojo {

    static final String UB_BRANCH_PREFIX = "refs/ubuilds/";

    // Branch name shape: refs/ubuilds//{mergeRequestId}
    static final String UB_BRANCH_REGEX = UB_BRANCH_PREFIX + "(?<mergeRequestId>[0-9]+)";

    private static final Pattern UB_BRANCH_PATTERN = Pattern.compile(UB_BRANCH_REGEX);

    private final String targetBranchName;

    private final String mergeRequestId;

    /**
     * Constructor
     *
     * @param targetBranchName the unbreakable branch
     */
    private UbBranchPojo(@Nonnull String targetBranchName) {
        this.targetBranchName = targetBranchName;
        this.mergeRequestId = getMergeRequestIdFromUbBranchName(targetBranchName);
    }

    /**
     * Get the target branch name
     *
     * @return the name of the target branch name
     */
    String getTargetBranchName() {
        return targetBranchName;
    }

    /**
     * Get the merge request id
     *
     * @return the merge request id
     */
    String getMergeRequestId() {
        return mergeRequestId;
    }

    static UbBranchPojo fromUbBranchName(@Nonnull String ubBranchName) {
        UbBranchPojo result = null;
        String id = getMergeRequestIdFromUbBranchName(ubBranchName);
        if (id != null) {
            result = new UbBranchPojo(ubBranchName);
        }
        return result;
    }

    static String getMergeRequestIdFromUbBranchName(@Nonnull String ubBranchName) {
        Matcher m = UB_BRANCH_PATTERN.matcher(ubBranchName);
        if (m.matches()) {
            return m.group("mergeRequestId");
        }
        return null;
    }
}
