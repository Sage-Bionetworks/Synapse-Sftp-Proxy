package org.sagebionetworks.web.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletOutputStream;

/**
 * Class wraps a ByteArrayOutputStream to allow for easy access to the ServletOutputStream contents
 * @author jayhodgson
 *
 */
public class ByteArrayServletOutputStream extends ServletOutputStream {
	private ByteArrayOutputStream baos = new ByteArrayOutputStream();
	
	@Override
	public void write(byte[] byteArray) throws IOException {
		baos.write(byteArray);
	}
	
	public String asString() throws UnsupportedEncodingException {
		return baos.toString("UTF-8");
	}
	
	@Override
	public void flush() throws IOException {
		baos.flush();
	}
	
	@Override
	public void close() throws IOException {
		baos.close();
	}
	
	@Override
	public void write(int b) throws IOException {
		baos.write(b);
	}
}
