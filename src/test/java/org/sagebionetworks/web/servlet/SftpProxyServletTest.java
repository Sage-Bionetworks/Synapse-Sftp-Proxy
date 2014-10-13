package org.sagebionetworks.web.servlet;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.web.server.servlet.SftpProxyServlet;
import org.sagebionetworks.web.server.servlet.filter.BasicAuthFilter;
import org.sagebionetworks.web.server.servlet.filter.Credentials;
import org.sagebionetworks.web.server.servlet.filter.SFTPFileMetadata;
import org.sagebionetworks.web.servlet.filter.BasicAuthFilterTest;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import static org.mockito.Mockito.*;

public class SftpProxyServletTest {
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse;
	String username, password;
	SftpProxyServlet servlet;
	JSch mockJsch;
	Session mockSession;
	ChannelSftp mockChannel;
	
	@Before
	public void setUp() throws UnsupportedEncodingException, JSchException {
		mockJsch = mock(JSch.class);
		mockSession = mock(Session.class);
		when(mockJsch.getSession(anyString(), anyString(), anyInt())).thenReturn(mockSession);
		mockChannel = mock(ChannelSftp.class);
		when(mockSession.openChannel(anyString())).thenReturn(mockChannel);
		servlet = new SftpProxyServlet();
		servlet.setJsch(mockJsch);
		
		username = "bob";
		password = "password1";
		mockRequest = mock(HttpServletRequest.class);
		mockResponse = mock(HttpServletResponse.class);
		BasicAuthFilterTest.setupCredentials(mockRequest, username, password);
	}

	@Test
	public void testChangeToRemoteUploadDirectory() throws SftpException {
		List<String> path = new ArrayList<String>();
		String dir1 = "d1";
		String dir2 = "d2";
		path.add(dir1);
		path.add(dir2);
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		//and verify the values
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(mockChannel, times(2)).cd(captor.capture());
		List<String> values = captor.getAllValues();
		assertEquals(dir1, values.get(0));
		assertEquals(dir2, values.get(1));
	}
	
	@Test
	public void testChangeToRemoteUploadDirectoryMkdir() throws SftpException {
		List<String> path = new ArrayList<String>();
		String dir1 = "d1";
		path.add(dir1);
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		//initially throw an exception, then do nothing (do not throw an exception on the "cd" command)
		doThrow(new SftpException(1, "fake exception when changing directory")).doNothing().when(mockChannel).cd(anyString());
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		verify(mockChannel).mkdir(dir1);
	}
	

	@Test
	public void testChangeToRemoteUploadDirectoryEmpty() throws SftpException {
		List<String> path = new ArrayList<String>();
		SFTPFileMetadata metadata = new SFTPFileMetadata("sagebase.org", path);
		servlet.changeToRemoteUploadDirectory(metadata, mockChannel);
		
		verify(mockChannel, never()).cd(anyString());
		verify(mockChannel, never()).mkdir(anyString());
	}
}
