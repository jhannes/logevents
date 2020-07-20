package org.logeventsdemo.servlets;

import org.logevents.query.LogEventSource;
import org.logevents.util.openid.OpenIdConfiguration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class LogViewerApplication implements ServletContextListener {

    private LogViewerServlet servlet = new LogViewerServlet();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.addServlet("root", servlet).addMapping("/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }

    public void setLogEventSource(LogEventSource logEventSource) {
        servlet.setLogEventSource(logEventSource);
    }

    public void setCookieEncryptionKey(String cookiePassword) {
        servlet.setCookieEncryptionKey(cookiePassword);
    }

    public void setOpenIdConfiguration(OpenIdConfiguration openIdConfiguration) {
        servlet.setOpenIdConfiguration(openIdConfiguration);
    }
}
