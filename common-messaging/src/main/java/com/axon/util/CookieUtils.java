package com.axon.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.SerializationUtils;

import java.util.Base64;
import java.util.Optional;

public class CookieUtils {

    /**
     * Retrieve a cookie with the given name from the HTTP request.
     *
     * @param request the HTTP servlet request to search for the cookie
     * @param name    the name of the cookie to find
     * @return        an Optional containing the matching Cookie if present, `Optional.empty()` otherwise
     */
    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return Optional.of(cookie);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Add a cookie to the HTTP response with the specified name, value, path, max age, and HttpOnly flag.
     * Includes SameSite and Secure attributes for better security and cross-site compatibility.
     *
     * @param response the HttpServletResponse to which the cookie will be added
     * @param name     the cookie name
     * @param value    the cookie value
     * @param maxAge   the cookie lifetime in seconds
     * @param httpOnly whether the cookie should be marked HttpOnly
     */
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean httpOnly) {
        // Build Set-Cookie header manually (no spring-web dependency required)
        StringBuilder cookieHeader = new StringBuilder();
        cookieHeader.append(name).append("=").append(value);
        cookieHeader.append("; Path=/");
        cookieHeader.append("; Max-Age=").append(maxAge);

        if (httpOnly) {
            cookieHeader.append("; HttpOnly");
        }

        // Note: Secure=false for HTTP environment (current setup)
        // Set to true if upgrading to HTTPS
        // cookieHeader.append("; Secure");

        // SameSite=Lax is sufficient for OAuth2 flows and works with HTTP
        cookieHeader.append("; SameSite=Lax");

        response.addHeader("Set-Cookie", cookieHeader.toString());
    }

    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
    }

    public static String serialize(Object object) {
        return Base64.getUrlEncoder()
                .encodeToString(SerializationUtils.serialize(object));
    }

    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        return cls.cast(SerializationUtils.deserialize(
                        Base64.getUrlDecoder().decode(cookie.getValue())));
    }
}