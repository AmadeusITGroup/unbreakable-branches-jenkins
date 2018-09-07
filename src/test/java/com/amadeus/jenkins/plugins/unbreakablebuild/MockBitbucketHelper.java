package com.amadeus.jenkins.plugins.unbreakablebuild;


import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class MockBitbucketHelper {

    // we compile it in order to be displayed nicely in intellij, (checkNotificationReceived visually the regex)
    // + the natural checks by the Pattern.compile (not valid regex -> explode)
    // and just use the valid String :)
    static final String BB_UB_API_REGEX = Pattern.compile(
            "/rest/ubuild/1\\.0/projects/\\w+/repos/\\w+/commits/\\p{XDigit}+/notify"
    ).toString();

    static void stubNotificationWith(WireMockRule wireMockRule) {
        stubNotificationWith(wireMockRule, 200, "Everything is OK");
    }


    static void stubNotificationWith(WireMockRule wireMockRule, int statusCode, String message) {
        String body = "";
        if (message != null) {
            body = "{ \"message\": " + message + " }";
        }
        wireMockRule.stubFor(
                post(
                        urlMatching(BB_UB_API_REGEX)

                ).willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-type", "application/json")
                        .withBody(body)

                )
        );
    }

    static void checkNotificationReceived(UbJenkinsEnvBean.Status status, String username, String password) {
        verify(
                postRequestedFor(urlMatching("/rest/ubuild/" + UbJenkinsEnvBean.BITBUCKET_API_VERSION + "/.*"))
                        .withRequestBody(matching(".*\"result\":\"" + status.toString() + "\".*"))
                        .withBasicAuth(new BasicCredentials(username, password))
        );
    }

}
