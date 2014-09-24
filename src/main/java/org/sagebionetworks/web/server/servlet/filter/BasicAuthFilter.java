package org.sagebionetworks.web.server.servlet.filter;

import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sagebionetworks.web.server.servlet.SftpProxyServlet;
import org.springframework.http.HttpStatus;

public class BasicAuthFilter implements Filter {

	public void destroy() {
	}

	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		Credentials credentials = getCredentials(request);
		Boolean requestIsAuthorized = credentials != null;

		if (requestIsAuthorized) {
			// proceed
			chain.doFilter(request, response);
		} else {
			// challenge
			SFTPFileMetadata metadata = SFTPFileMetadata.parseUrl(request.getParameter(SftpProxyServlet.SFTP_URL_PARAM));
			respondWithChallenge(response, metadata.getHost());
		}
	}

	public static void respondWithChallenge(HttpServletResponse response, String host) {
		// challenge
		//http://docs.oracle.com/cd/E21455_01/common/tutorials/authn_http_basic.html
		//respond with a 401 asking for basic http authentication
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setHeader("WWW-Authenticate", HttpServletRequest.BASIC_AUTH + " realm=\"Connection to "+host+ "\"");

	}
	
	public static Credentials getCredentials(HttpServletRequest request) {
		String authHeader = request.getHeader("Authorization");
		if (authHeader != null) {
			StringTokenizer st = new StringTokenizer(authHeader);
			if (st.hasMoreTokens()) {
				String basic = st.nextToken();
				if (basic.equalsIgnoreCase(HttpServletRequest.BASIC_AUTH)) {
					sun.misc.BASE64Decoder dec = new sun.misc.BASE64Decoder();
					try {
						String credentials = new String(dec.decodeBuffer(st.nextToken()));
						int p = credentials.indexOf(":");
						if (p != -1) {
							String login = credentials.substring(0, p).trim();
							String password = credentials.substring(p + 1).trim();
							return new Credentials(login, password);
						}
					} catch (IOException e) {
						return null;
					}
				}
			}
		}
		return null;
	}

	public void init(FilterConfig arg0) throws ServletException {
	}

}
