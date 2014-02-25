package sfrest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;
import static sfrest.Environment.PRODUCTION;
import static sfrest.SFExceptionMatcher.*;

public class UserPassTokenProviderTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private UserPassTokenProvider tokenProvider;

    @Before
    public void init() {
        tokenProvider = new UserPassTokenProvider();
        tokenProvider.setEnvironment(PRODUCTION);
        tokenProvider.setClientId("3MVG9Y6d_Btp4xp5Xqs8.5xmFm2lAaZDOz2aeLy6mH.p6RXoshrl1SMWhsDoF10Fwi.cVo92zI.RKQguP0bUc");
        tokenProvider.setClientSecret("781889688054271860");
        tokenProvider.setUsername("sanlyfang@gmail.com");
        tokenProvider.setPassword("test1234");
        tokenProvider.setSecurityToken("9hCzimEBARhsnCxhKpeqdaQBX");
    }

    @After
    public void clean() {
        tokenProvider = null;
    }

    @Test
    public void testRequestToken() {
        Token token = tokenProvider.requestToken();
        assertNotNull(token);
        assertNotNull(token.getId());
        assertNotNull(token.getIssueTime());
        assertNotNull(token.getInstanceUrl());
        assertNotNull(token.getSignature());
        assertNotNull(token.getAccessToken());
        assertNull(token.getRefreshToken());
        assertFalse(tokenProvider.isRefreshable(token));
        System.out.println(token);
    }

    @Test
    public void testWrongClientId() {
        thrown.expect(INVALID_ClIENT_ID);

        tokenProvider.setClientId("wrong");
        tokenProvider.requestToken();
    }

    @Test
    public void testWrongClientSecret() {
        thrown.expect(INVALID_CLIENT);

        tokenProvider.setClientSecret("wrong");
        tokenProvider.requestToken();
    }

    @Test
    public void testWrongUsername() {
        thrown.expect(INVALID_GRANT);

        tokenProvider.setUsername("wrong");
        tokenProvider.requestToken();
    }

    @Test
    public void testWrongPassword() {
        thrown.expect(INVALID_GRANT);

        tokenProvider.setPassword("wrong");
        tokenProvider.requestToken();
    }

    @Test
    public void testMissingSecurityToken() {
        thrown.expect(INVALID_GRANT);

        tokenProvider.setSecurityToken("");
        tokenProvider.requestToken();
    }

    @Test
    public void testRevokeToken() {
        Token token = tokenProvider.requestToken();
        tokenProvider.revokeToken(token);
    }
}
