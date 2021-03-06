package sfrest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class SFRestClient implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SFRestClient.class);

    private static final boolean httpClientPresent =
            ClassUtils.isPresent("org.apache.http.impl.client.CloseableHttpClient", SFRestClient.class.getClassLoader());

    public static final String BASE_URI_APEX = "/services/apexrest";
    public static final String BASE_URI_REST = "/services/data/v29.0"; // TODO: auto-detect latest version

    private static final ParameterizedTypeReference<String> TYPE_STRING = new ParameterizedTypeReference<String>() {
    };
    private static final ParameterizedTypeReference<Object> TYPE_OBJECT = new ParameterizedTypeReference<Object>() {
    };
    private static final ParameterizedTypeReference<Map<String, ?>> TYPE_MAP = new ParameterizedTypeReference<Map<String, ?>>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, ?>>> TYPE_MAPLIST = new ParameterizedTypeReference<List<Map<String, ?>>>() {
    };
    private static final ParameterizedTypeReference<List<?>> TYPE_LIST = new ParameterizedTypeReference<List<?>>() {
    };

    private TokenProvider tokenProvider;
    private TokenStorage tokenStorage;
    private SFRestTemplate template;
    private HttpComponentsClientHttpRequestFactory httpClientRequestFactory; // Used only if http client library ( >= 4.3 ) is present.

    public TokenProvider getTokenProvider() {
        return tokenProvider;
    }

    public TokenStorage getTokenStorage() {
        return tokenStorage;
    }

    public SFRestTemplate getRestTemplate() {
        return template;
    }

    public SFRestClient(UserPassTokenProvider systemTokenProvider) {
        this(systemTokenProvider, new DefaultTokenStorage());
    }

    public SFRestClient(TokenProvider tokenProvider, TokenStorage tokenStorage) {
        this.tokenProvider = tokenProvider;
        this.tokenStorage = tokenStorage;
        this.template = new SFRestTemplate();

        if (httpClientPresent) {
            httpClientRequestFactory = new HttpComponentsClientHttpRequestFactory();
            this.template.setRequestFactory(httpClientRequestFactory);
        }
    }

    /**
     * Returns response as json string.
     */
    public String getString(String uri, HttpMethod method, Object requestBody, Object... uriVariables) {
        return execute(uri, method, requestBody, TYPE_STRING, uriVariables);
    }

    public Object getObject(String uri, HttpMethod method, Object requestBody, Object... uriVariables) {
        return execute(uri, method, requestBody, TYPE_OBJECT, uriVariables);
    }

    public Map<String, ?> getMap(String uri, HttpMethod method, Object requestBody, Object... uriVariables) {
        return execute(uri, method, requestBody, TYPE_MAP, uriVariables);
    }

    public List<Map<String, ?>> getMapList(String uri, HttpMethod method, Object requestBody, Object... uriVariables) {
        return execute(uri, method, requestBody, TYPE_MAPLIST, uriVariables);
    }

    public List<?> getList(String uri, HttpMethod method, Object requestBody, Object... uriVariables) {
        return execute(uri, method, requestBody, TYPE_LIST, uriVariables);
    }

    public Map<String, ?> listSObjects() {
        return getMap(BASE_URI_REST + "/sobjects", HttpMethod.GET, null);
    }

    public Map<String, ?> getSObjectMetadata(String type, boolean describeDetails) {
        String uri = BASE_URI_REST + "/sobjects/{type}";
        if (describeDetails) {
            uri += "/describe";
        }

        return getMap(uri, HttpMethod.GET, null, type);
    }

    public Map<String, ?> getSObject(String type, String id, String... fields) {
        String uri = BASE_URI_REST + "/sobjects/{type}/{id}";

        if (fields.length > 0) {
            uri += "?fields=" + StringUtils.arrayToCommaDelimitedString(fields);
        }

        return getMap(uri, HttpMethod.GET, null, type, id);
    }

    public List<Map<String, ?>> query(String soql) {
        return query(new Query(soql)).getRecords();
    }

    public QueryResult query(Query query) {
        String uri = query.getNextUri();
        if (uri == null) {
            uri = BASE_URI_REST + "/query/?q=" + query.getSoql();
        }

        Map<String, ?> ret = getMap(uri, HttpMethod.GET, null);

        QueryResult qResult = new QueryResult();
        qResult.setTotalSize((Integer) ret.get("totalSize"));
        qResult.setDone((Boolean) ret.get("done"));
        qResult.setRecords((List) ret.get("records"));

        query.setNextUri(!qResult.isDone() ? (String) ret.get("nextRecordsUrl") : null);
        qResult.setQuery(query);

        return qResult;
    }

    public Environment getEnvironment() {
        return tokenProvider.getEnvironment();
    }

    public Map<String, ?> getCurrentUser() {
        String id = getToken().getId();
        String userId = id.substring(id.lastIndexOf('/') + 1);

        return getSObject("User", userId);
    }

    public <T> T execute(String uri, HttpMethod method, Object requestBody, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        Token token = getToken();

        if (!uri.startsWith("http")) {
            uri = token.getInstanceUrl() + (uri.startsWith("/") ? "" : "/") + uri;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token.getAccessToken());
        headers.add("Content-Type", "application/json;charset=UTF-8");

        try {
            return template.exchange(uri, method, new HttpEntity<>(requestBody, headers), responseType, uriVariables).getBody();
        } catch (TokenException e) {
            tokenStorage.clearToken();
            logger.debug("Invalid token cleared successfully");

            throw e;
        }
    }

    private Token getToken() {
        Token token = tokenStorage.getToken();
        if (token == null) {
            logger.debug("Token not found, requesting new token...");
            token = tokenProvider.requestToken(template);
            logger.debug("Got token: {}", token);

            tokenStorage.saveToken(token);
            logger.debug("Token saved successfully");
        }

        return token;
    }

    @Override
    public void destroy() throws Exception {
        if (httpClientRequestFactory != null) {
            httpClientRequestFactory.destroy();
        }
    }

    private static class DefaultTokenStorage implements TokenStorage {

        private Token token;

        @Override
        public Token getToken() {
            return token;
        }

        @Override
        public void saveToken(Token token) {
            this.token = token;
        }

        @Override
        public void clearToken() {
            token = null;
        }
    }
}
