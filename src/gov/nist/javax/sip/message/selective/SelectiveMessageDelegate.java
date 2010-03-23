/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package gov.nist.javax.sip.message.selective;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.parser.HeaderParser;
import gov.nist.javax.sip.parser.ParserFactory;
import gov.nist.javax.sip.parser.selective.SelectiveMessage;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SelectiveMessageDelegate implements SelectiveMessage {
	
	private Map<String, String> headersNotParsed = new ConcurrentHashMap<String, String>(0);
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.parser.selective.SelectiveMessage#addHeaderNotParsed(java.lang.String, java.lang.String)
	 */	
	public void addHeaderNotParsed(String headerName, String header) {
		if(header.endsWith("\n")) {
			headersNotParsed.put(headerName.toLowerCase(), header);
		} else {
			headersNotParsed.put(headerName.toLowerCase(), header + "\n");
		}
	}
		
	public Iterator<String> getHeaderValuesNotParsed() {
		return headersNotParsed.values().iterator();
	}
	public Iterator<String> getHeaderNamesNotParsed() {
		return headersNotParsed.keySet().iterator();
	}
	
	public Map<String, String> getHeadersNotParsed() {
		return headersNotParsed;
	}
	
	public SIPHeader parseHeader(String headerName, boolean remove) {
		String header = null;
		if(remove) {
			header = headersNotParsed.remove(headerName);
		} else {
			header = headersNotParsed.get(headerName);
		}
		if(header != null) {
			HeaderParser headerParser = null;
	        try {
	            headerParser = ParserFactory.createParser(header);
	            return headerParser.parse();
	        } catch (ParseException ex) {
	            throw new IllegalArgumentException("Following header couldn't be parsed " + header + " for header name " + headerName, ex);
	        }            
		}
		return null;
	}

	public String getHeaderUnparsed(String headerName) {
		String header = headersNotParsed.get(headerName);
		return header;
	}

	public String removeHeaderNotParsed(String lowerCase) {
		return headersNotParsed.remove(lowerCase);
	}

}
