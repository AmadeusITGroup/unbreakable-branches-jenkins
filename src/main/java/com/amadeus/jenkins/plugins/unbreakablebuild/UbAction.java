package com.amadeus.jenkins.plugins.unbreakablebuild;

import hudson.model.Action;

/**
 * This action has for sole purpose to be added to a Run
 * in order to tell that an unbreakableBuild step was called
 */
public class UbAction implements Action {

    /**
     * Gets the file name of the icon. here null.
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * Gets the string to be displayed.
     * <p>
     * The convention is to capitalize the first letter of each word,
     * such as "Test Result".
     *
     * @return Can be null in case the action is hidden.
     */
    @Override
    public String getDisplayName() {
        return "Unbreakable Build Action";
    }

    /**
     * Gets the URL path name. here null.
     */
    @Override
    public String getUrlName() {
        return null;
    }
}
