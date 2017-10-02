package com.ashtonit.odb.realm;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class TestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        final Writer w = resp.getWriter();
        w.write(getClass().getName());
        w.flush();
        w.close();
    }
}
