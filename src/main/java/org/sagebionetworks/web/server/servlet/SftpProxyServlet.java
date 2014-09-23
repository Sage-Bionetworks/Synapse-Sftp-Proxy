package org.sagebionetworks.web.server.servlet;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.http.HttpStatus;
import org.sagebionetworks.web.server.servlet.filter.BasicAuthFilter;
import org.sagebionetworks.web.server.servlet.filter.Credentials;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class SftpProxyServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private JSch jsch = new JSch();
	protected static final ThreadLocal<HttpServletRequest> perThreadRequest = new ThreadLocal<HttpServletRequest>();

	@Override
	protected void service(HttpServletRequest arg0, HttpServletResponse arg1) throws ServletException, IOException {
		SftpProxyServlet.perThreadRequest.set(arg0);
		super.service(arg0, arg1);
	}

	public void sftpUploadFile(HttpServletRequest request) throws FileUploadException, IOException, ServletException {
		ServletFileUpload upload = new ServletFileUpload();
		FileItemIterator iter = upload.getItemIterator(request);
		while (iter.hasNext()) {
			// should be one in this case
			FileItemStream item = iter.next();
			String name = item.getFieldName();
			InputStream stream = item.openStream();
			String fileName = item.getName();
			if (fileName.contains("\\")) {
				fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
			}

			Session session = getSession(request);
			try {
				Channel channel = session.openChannel("sftp");
				channel.connect();
				ChannelSftp sftpChannel = (ChannelSftp) channel;
				sftpChannel.put(stream, fileName);
				sftpChannel.exit();
			} catch (JSchException e) {
				throw new ServletException(e);
			} catch (SftpException e) {
				throw new ServletException(e);
			} finally {
				if (session != null)
					session.disconnect();
			}
		}
	}
	
	private Session getSession(HttpServletRequest request) throws ServletException {
		Credentials credentials = BasicAuthFilter.getCredentials(request);
		if (credentials == null) {
			throw new ServletException("Basic authorization required for SFTP connection.");
		}
		try {
			String host = request.getParameter("host");
			Session session = jsch.getSession(credentials.getUsername(), host, 22);
			session.setPassword(credentials.getPassword());
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			return session;
		} catch (JSchException e) {
			throw new ServletException(e);
		}
	}

	@Override
	public void service(ServletRequest arg0, ServletResponse arg1) throws ServletException, IOException {
		super.service(arg0, arg1);
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ServletOutputStream stream = response.getOutputStream();
		Session session = getSession(request);
		String path = request.getParameter("path");
		try {
			Channel channel = session.openChannel("sftp");
			channel.connect();
			ChannelSftp sftpChannel = (ChannelSftp) channel;
			sftpChannel.get(path, stream);
			sftpChannel.exit();
		} catch (JSchException e) {
			throw new ServletException(e);
		} catch (SftpException e) {
			throw new ServletException(e);
		} finally {
			if (session != null)
				session.disconnect();
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			sftpUploadFile(request);
			response.setStatus(HttpStatus.SC_OK);
		} catch (FileUploadException e) {
			throw new ServletException(e);
		}
	}
}
