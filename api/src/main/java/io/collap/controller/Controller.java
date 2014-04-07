package io.collap.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface Controller {

    public void execute (String remainingPath, HttpServletRequest request, HttpServletResponse response) throws IOException;

}