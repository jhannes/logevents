package org.logeventsdemo.servlets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TestServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(TestServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.info("Here we go!");
        if (req.getPathInfo() == null) {
            throw new IllegalArgumentException("SOmething went wrong");
        }

        if (req.getPathInfo().equals("/redirect")) {
            resp.sendRedirect("/");
            return;
        }

        log.info("Processing complete of " + req.getPathInfo());
        resp.getWriter().write("Hello world");
    }
}
