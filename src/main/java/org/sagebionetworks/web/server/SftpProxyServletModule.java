package org.sagebionetworks.web.server;

import org.sagebionetworks.web.server.servlet.SftpProxyServlet;
import org.sagebionetworks.web.server.servlet.filter.BasicAuthFilter;

import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

/**
 * Binds the service servlets to their paths and any other 
 * Guice binding required on the server side.
 * 
 */
public class SftpProxyServletModule extends ServletModule {
	

	@Override
	protected void configureServlets() {
		//look for basic authentication (for sftp request)
		filter("/Sftpproxy/*").through(BasicAuthFilter.class);
		bind(BasicAuthFilter.class).in(Singleton.class);
		
		//do sftp operations
		bind(SftpProxyServlet.class).in(Singleton.class);
		serve("/Sftpproxy/sftp").with(SftpProxyServlet.class);
	}
}
