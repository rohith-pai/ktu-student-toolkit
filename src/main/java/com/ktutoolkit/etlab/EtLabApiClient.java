package com.ktutoolkit.etlab;

import com.ktutoolkit.exceptions.LoginFailedException;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Minimal HTTP client for marian.etlab.in.
 *
 * <p>ETLab uses a PHP session cookie (MCESESSIONID) instead of a JWT. The
 * cookie value is what we call the "session token". It expires quickly, so
 * this class keeps the username/password and transparently logs in again
 * when a request is redirected back to the login page.</p>
 */
public class EtLabApiClient {
    public static final String DEFAULT_BASE_URL = "https://marian.etlab.in";

    private static final String LOGIN_PATH = "/user/login";
    private static final String SESSION_CHECK_PATH = "/user/dashboard";
    private static final String SESSION_COOKIE = "MCESESSIONID";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KTU-Student-Toolkit/1.0";

    private final String baseUrl;
    private final CookieManager cookieManager;
    private final HttpClient httpClient;

    private String username;
    private String password;
    private String sessionToken;

    public EtLabApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public EtLabApiClient(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Restores a previously saved MCESESSIONID so it can be validated before
     * asking the user for credentials again.
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
        if (token == null || token.isBlank()) {
            return;
        }
        cookieManager.getCookieStore().removeAll();
        List<HttpCookie> parsed = HttpCookie.parse(SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly");
        if (parsed.isEmpty()) {
            return;
        }
        HttpCookie cookie = parsed.get(0);
        cookie.setSecure(baseUrl.startsWith("https://"));
        cookieManager.getCookieStore().add(URI.create(baseUrl), cookie);
    }

    public void clearSession() {
        this.sessionToken = null;
        cookieManager.getCookieStore().removeAll();
    }

    /**
     * Logs in using the credentials already supplied through
     * {@link #setCredentials(String, String)}.
     */
    public void login() throws LoginFailedException, IOException {
        if (!hasCredentials()) {
            throw new LoginFailedException("No ETLab credentials were provided.");
        }
        login(username, password);
    }

    /**
     * Performs a fresh ETLab login and stores the resulting MCESESSIONID.
     */
    public void login(String username, String password) throws LoginFailedException, IOException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new LoginFailedException("Username and password cannot be empty.");
        }

        this.username = username;
        this.password = password;
        clearSession();

        // Load the login page first so ETLab issues its CSRF/session cookies.
        sendGet("/");

        String form = "LoginForm%5Busername%5D=" + encode(username)
                + "&LoginForm%5Bpassword%5D=" + encode(password)
                + "&yt0=";

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + LOGIN_PATH))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", baseUrl + "/")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        int status = response.statusCode();

        if (status == 200 && isLoginPage(response.body())) {
            throw new LoginFailedException("Invalid ETLab username or password.");
        }
        if (isRedirectToLogin(response)) {
            throw new LoginFailedException("Invalid ETLab username or password.");
        }
        if (status < 200 || status >= 400) {
            throw new LoginFailedException("ETLab login failed (HTTP " + status + ").");
        }

        String token = readSessionCookie();
        if (token == null || token.isBlank()) {
            throw new LoginFailedException("ETLab login succeeded but no session token was returned.");
        }
        this.sessionToken = token;

        // Make sure the cookie actually grants access before saving it.
        if (!isSessionValid()) {
            clearSession();
            throw new LoginFailedException("ETLab login could not be verified. Please try again.");
        }
    }

    /**
     * Checks the current MCESESSIONID against an authenticated page.
     * A redirect to /user/login means the session has expired.
     */
    public boolean isSessionValid() {
        if (sessionToken == null || sessionToken.isBlank()) {
            return false;
        }
        try {
            HttpResponse<String> response = sendGet(SESSION_CHECK_PATH);
            return response.statusCode() == 200 && !isLoginPage(response.body());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ensures there is a usable session, logging in again when needed.
     */
    public void ensureAuthenticated() throws LoginFailedException, IOException {
        if (isSessionValid()) {
            return;
        }
        if (!hasCredentials()) {
            throw new LoginFailedException("Your ETLab session has expired. Please log in again.");
        }
        login();
    }

    /**
     * Fetches an authenticated ETLab page. If the session expires between
     * validation and the request (or the server redirects to login), the
     * client logs in once more and retries the request.
     */
    public String get(String path) throws LoginFailedException, IOException {
        ensureAuthenticated();

        HttpResponse<String> response = sendGet(path);
        if (isRedirectToLogin(response) || (response.statusCode() == 200 && isLoginPage(response.body()))) {
            if (!hasCredentials()) {
                throw new LoginFailedException("Your ETLab session has expired. Please log in again.");
            }
            login();
            response = sendGet(path);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("ETLab request failed (HTTP " + response.statusCode() + ") for " + path);
        }
        if (isLoginPage(response.body())) {
            throw new LoginFailedException("Your ETLab session has expired. Please log in again.");
        }
        return response.body();
    }

    /**
     * Submits an authenticated HTML form (application/x-www-form-urlencoded)
     * and returns the resulting page. ETLab's monthly attendance report is
     * only available through this POST endpoint.
     */
    public String postForm(String path, java.util.Map<String, String> fields)
            throws LoginFailedException, IOException {
        ensureAuthenticated();

        HttpResponse<String> response = sendPostForm(path, fields);
        if (isRedirectToLogin(response)
                || (response.statusCode() == 200 && isLoginPage(response.body()))) {
            if (!hasCredentials()) {
                throw new LoginFailedException("Your ETLab session has expired. Please log in again.");
            }
            login();
            response = sendPostForm(path, fields);
        }

        // ETLab often answers form submissions with a redirect back to the
        // report page. Follow that single redirect manually.
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("location").orElse("");
            if (!location.isBlank()) {
                response = sendGet(location);
            }
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("ETLab request failed (HTTP " + response.statusCode() + ") for " + path);
        }
        if (isLoginPage(response.body())) {
            throw new LoginFailedException("Your ETLab session has expired. Please log in again.");
        }
        return response.body();
    }

    private HttpResponse<String> sendGet(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(path)))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        return send(request);
    }

    private HttpResponse<String> sendPostForm(String path, java.util.Map<String, String> fields)
            throws IOException {
        StringBuilder form = new StringBuilder();
        if (fields != null) {
            for (java.util.Map.Entry<String, String> field : fields.entrySet()) {
                if (form.length() > 0) {
                    form.append('&');
                }
                form.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
            }
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(path)))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", buildUrl(path))
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ETLab request was interrupted.", e);
        }
    }

    private String readSessionCookie() {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        for (HttpCookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean isRedirectToLogin(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 300 || status >= 400) {
            return false;
        }
        String location = response.headers().firstValue("location").orElse("");
        return location.contains(LOGIN_PATH);
    }

    private static boolean isLoginPage(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("Invalid username or password")
                || body.contains("id=\"login-form\"")
                || body.contains("LoginForm_username")
                || body.contains("LoginForm[username]");
    }

    private String buildUrl(String path) {
        if (path == null || path.isEmpty()) {
            return baseUrl + "/";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
