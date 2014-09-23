package org.sagebionetworks.web.server.servlet.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;

public class BasicAuthFilter implements Filter {

	public void destroy() {
	}

	public void doFilter(ServletRequest servletRequest,
			ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		Boolean requestIsAuthorized = true;


		if (requestIsAuthorized) {
			// proceed
			chain.doFilter(request, response);
		} else {
			//challenge
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setHeader("WWW-Authenticate", "authenticate simpleAuth");
		}
		
	}


	public void init(FilterConfig arg0) throws ServletException {
	}

	
	
	
}
