package org.logeventsdemo.servlets;

import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.logevents.mdc.DynamicMDCAdapter;
import org.logevents.optional.servlets.HttpServletRequestMDC;
import org.slf4j.MDC;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

public class LogEventServletFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        MDC.clear();
        try (DynamicMDCAdapter.Cleanup cleanup = HttpServletRequestMDC.put(request).retainIfIncomplete()) {
            Principal principal = () -> "Some User";
            ((Request) request).setAuthentication(createAuthentication(principal));
            chain.doFilter(request, response);
            cleanup.complete();
        }
    }

    private Authentication createAuthentication(Principal principal) {
        return new UserAuthentication("identitifed", new DefaultUserIdentity(
                new Subject(false, Collections.singleton(principal), Collections.emptySet(), Collections.emptySet()),
                principal,
                new String[0]
        ));
    }
}
