package org.sagebionetworks.web.servlet.filter;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.web.server.servlet.filter.BasicAuthFilter;
import org.sagebionetworks.web.server.servlet.filter.Credentials;

import static org.mockito.Mockito.*;

public class BasicAuthFilterTest {
	HttpServletRequest mockRequest;

	@Before
	public void setUp() {
		mockRequest = mock(HttpServletRequest.class);
	}

	public static void setupCredentials(HttpServletRequest mockRequest, String username, String password) throws UnsupportedEncodingException {
		String credentialsString = username + ":" + password;
		byte[] encoded = Base64.encodeBase64(credentialsString.getBytes("UTF-8"));
		when(mockRequest.getHeader(BasicAuthFilter.AUTHORIZATION_HEADER)).thenReturn(HttpServletRequest.BASIC_AUTH + " " + new String(encoded, "UTF-8"));
	}

	@Test
	public void testGetCredentials() throws UnsupportedEncodingException {
		String user = "BruceLee";
		String password = "ender dragon";
		setupCredentials(mockRequest, user, password);
		Credentials credentials = BasicAuthFilter.getCredentials(mockRequest);
		assertEquals(user, credentials.getUsername());
		assertEquals(password, credentials.getPassword());
	}

	@Test
	public void testGetCredentialsMissingAuthHeader() {
		assertNull(BasicAuthFilter.getCredentials(mockRequest));
	}
}
