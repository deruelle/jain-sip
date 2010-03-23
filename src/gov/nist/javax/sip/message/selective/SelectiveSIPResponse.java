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

import gov.nist.core.InternalErrorHandler;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.selective.SelectiveMessage;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.sip.header.Header;
import javax.sip.header.ViaHeader;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SelectiveSIPResponse extends SIPResponse implements SelectiveMessage {

	SelectiveMessageDelegate delegate;
	private static Set<String> headersToParse;		
	
	public SelectiveSIPResponse(Set<String> headersToParse) {
		SelectiveSIPResponse.headersToParse = headersToParse;
		delegate = new SelectiveMessageDelegate();
	}
	
	@Override
	public void addHeaderNotParsed(String headerName, String header) {
		delegate.addHeaderNotParsed(headerName, header);
	}			
	
	@Override
	protected Header getHeaderLowerCase(String lowerCaseHeaderName) {
		if(!headersToParse.contains(lowerCaseHeaderName)) {
			SIPHeader sipHeader = delegate.parseHeader(lowerCaseHeaderName, true);
			// once the header is parsed we have to add it to the standard list of headers since
			// the application can keep the ref to it and modify it, the only way to to get
			// the modifications appear in encode is to add it
			if(sipHeader != null) {
				super.addHeader(sipHeader);
			}
		}
		return super.getHeaderLowerCase(lowerCaseHeaderName);
	}		
	
	@Override
	public SIPHeader getSIPHeaderListLowerCase(String lowerCaseHeaderName) {
		if(!headersToParse.contains(lowerCaseHeaderName)) {
			SIPHeader sipHeader = delegate.parseHeader(lowerCaseHeaderName, true);
			// once the header is parsed we have to add it to the standard list of headers since
			// the application can keep the ref to it and modify it, the only way to to get
			// the modifications appear in encode is to add it
			if(sipHeader != null) {
				super.addHeader(sipHeader);
			}
		}
		return super.getSIPHeaderListLowerCase(lowerCaseHeaderName);
	}				
	
//	@Override
//	public void attachHeader(SIPHeader header, boolean replaceFlag, boolean top)
//			throws SIPDuplicateHeaderException {
//		if(!headersToParse.contains(header.getName().toLowerCase())) {
//			delegate.addHeader(header.getName().toLowerCase(), header.toString(), replaceFlag, top);
//		}
//		super.attachHeader(header, replaceFlag, top);
//	}
	
	@Override
	public String encode() {
		String retval;
        if (statusLine != null)
            retval = statusLine.encode() + encodeSelective();
        else
            retval = encodeSelective();
        return retval ;		
	}
	
	public String encodeSelective() {
        StringBuilder encoding = new StringBuilder();
        Iterator<SIPHeader> it = this.headers.iterator();

        while (it.hasNext()) {
            SIPHeader siphdr = (SIPHeader) it.next();
            if (!(siphdr instanceof ContentLength))
                siphdr.encode(encoding);
        }
        
        //Append the unparsed headers
        Iterator<String> nonParsedHeadersIt = this.delegate.getHeaderValuesNotParsed();

        while (nonParsedHeadersIt.hasNext()) {
            String nonParsedHeader = nonParsedHeadersIt.next();
            encoding.append(nonParsedHeader);
        }
        
        // Append the unrecognized headers. Headers that are not
        // recognized are passed through unchanged.
        if(unrecognizedHeaders != null) {
	        for (String unrecognized : unrecognizedHeaders) {
	            encoding.append(unrecognized).append(NEWLINE);
	        }
        }

        contentLengthHeader.encode(encoding).append(NEWLINE);

        if (this.messageContentObject != null) {
            String mbody = this.getContent().toString();

            encoding.append(mbody);
        } else if (this.messageContent != null || this.messageContentBytes != null) {

            String content = null;
            try {
                if (messageContent != null)
                    content = messageContent;
                else {
                	// JvB: Check for 'charset' parameter which overrides the default UTF-8
                    content = new String(messageContentBytes, getCharset() );
                }
            } catch (UnsupportedEncodingException ex) {
            	InternalErrorHandler.handleException(ex);
            }

            encoding.append(content);
        }
        return encoding.toString();
	}
	
	public byte[] encodeAsBytesSelective(String transport) {		
        // JvB: added to fix case where application provides the wrong transport
        // in the topmost Via header
        ViaHeader topVia = (ViaHeader) this.getHeader(ViaHeader.NAME);
        try {
            topVia.setTransport(transport);
        } catch (ParseException e) {
            InternalErrorHandler.handleException(e);
        }

        StringBuilder encoding = new StringBuilder();
        synchronized (this.headers) {
            Iterator<SIPHeader> it = this.headers.iterator();

            while (it.hasNext()) {
                SIPHeader siphdr = (SIPHeader) it.next();
                if (!(siphdr instanceof ContentLength))
                    siphdr.encode(encoding);

            }
        }
        // Append the unparsed headers
        Iterator<String> nonParsedHeadersIt = this.delegate.getHeaderValuesNotParsed();

        while (nonParsedHeadersIt.hasNext()) {
            String nonParsedHeader = nonParsedHeadersIt.next();
            encoding.append(nonParsedHeader);
        }
        
        contentLengthHeader.encode(encoding);
        encoding.append(NEWLINE);

        byte[] retval = null;
        byte[] content = this.getRawContent();
        if (content != null) {
            // Append the content

            byte[] msgarray = null;
            try {
                msgarray = encoding.toString().getBytes( getCharset() );
            } catch (UnsupportedEncodingException ex) {
                InternalErrorHandler.handleException(ex);
            }

            retval = new byte[msgarray.length + content.length];
            System.arraycopy(msgarray, 0, retval, 0, msgarray.length);
            System.arraycopy(content, 0, retval, msgarray.length, content.length);
        } else {
            // Message content does not exist.

            try {
                retval = encoding.toString().getBytes( getCharset() );
            } catch (UnsupportedEncodingException ex) {
                InternalErrorHandler.handleException(ex);
            }
        }
        return retval;
	}
		
	@Override
	public byte[] encodeAsBytes(String transport) {
		byte[] slbytes = null;
        if (statusLine != null) {
            try {
                slbytes = statusLine.encode().getBytes("UTF-8");
            } catch (UnsupportedEncodingException ex) {
                InternalErrorHandler.handleException(ex);
            }
        }
        byte[] superbytes = encodeAsBytesSelective(transport );
        byte[] retval = new byte[slbytes.length + superbytes.length];
        System.arraycopy(slbytes, 0, retval, 0, slbytes.length);
        System.arraycopy(superbytes, 0, retval, slbytes.length,
                superbytes.length);
        return retval;
	}
	
	@Override
	public StringBuilder encodeMessage(StringBuilder retval) {
		if (statusLine != null) {
            statusLine.encode(retval);
            encodeSIPHeaders(retval);
        } else {
            retval = encodeSIPHeaders(retval);
        }
        return retval;
	}
	
	@Override
	protected StringBuilder encodeSIPHeaders(StringBuilder encoding) {
//		StringBuilder encoding = new StringBuilder();
		Iterator<SIPHeader> it = this.headers.iterator();

		while (it.hasNext()) {
			SIPHeader siphdr = (SIPHeader) it.next();
			if (!(siphdr instanceof ContentLength))
				siphdr.encode(encoding);
		}

		// Append the unparsed headers
        Iterator<String> nonParsedHeadersIt = this.delegate.getHeaderValuesNotParsed();

        while (nonParsedHeadersIt.hasNext()) {
            String nonParsedHeader = nonParsedHeadersIt.next();
            encoding.append(nonParsedHeader);
        }
        
		return contentLengthHeader.encode(encoding).append(NEWLINE);
	}
	
//	@Override
//	public boolean equals(Object other) {
//		if (!this.getClass().equals(other.getClass()))
//            return false;
//        SIPRequest that = (SIPRequest) other;
//
//        return requestLine.equals(that.requestLine) && super.equals(other);
//	}
			
	@Override
	public String getHeaderAsFormattedString(String name) {
		if(!headersToParse.contains(name.toLowerCase())) {
			String unparsedHeader = delegate.getHeaderUnparsed(name.toLowerCase());
			if(unparsedHeader != null) {
				return unparsedHeader;
			}
		}
		String lowerCaseName = name.toLowerCase();
        if (this.headerTable.containsKey(lowerCaseName)) {
            return this.headerTable.get(lowerCaseName).toString();
        } else {
            return this.getHeader(name).toString();
        }
	}
	
	@Override
	public ListIterator<String> getHeaderNames() {
		Iterator<SIPHeader> li = this.headers.iterator();
        LinkedList<String> retval = new LinkedList<String>();
        while (li.hasNext()) {
            SIPHeader sipHeader = (SIPHeader) li.next();
            String name = sipHeader.getName();
            retval.add(name);
        }
        // Append the unparsed headers
        Iterator<String> nonParsedHeadersIt = this.delegate.getHeaderValuesNotParsed();

        while (nonParsedHeadersIt.hasNext()) {
            String nonParsedHeader = nonParsedHeadersIt.next();
            retval.add(nonParsedHeader);
        }
        
        return retval.listIterator();
	}
	
	@Override
	public Iterator<SIPHeader> getHeaders() {
		// Append the unparsed headers
        Iterator<String> nonParsedHeadersIt = this.delegate.getHeaderNamesNotParsed();

        while (nonParsedHeadersIt.hasNext()) {
            String nonParsedHeaderName = nonParsedHeadersIt.next();
            SIPHeader sipHeader = delegate.parseHeader(nonParsedHeaderName, true);
            // once the header is parsed we have to add it to the standard list of headers since
			// the application can keep the ref to it and modify it, the only way to to get
			// the modifications appear in encode is to add it
            if(sipHeader != null) {
				super.addHeader(sipHeader);
			}
        }        		      
        
        return super.getHeaders();
	}
	
	@Override
	public ListIterator<SIPHeader> getHeaders(String headerName) {
		if(!headersToParse.contains(headerName.toLowerCase())) {
			SIPHeader sipHeader = delegate.parseHeader(headerName.toLowerCase(), true);
			// once the header is parsed we have to add it to the standard list of headers since
			// the application can keep the ref to it and modify it, the only way to to get
			// the modifications appear in encode is to add it			
			if(sipHeader != null) {
				super.addHeader(sipHeader);
			}
		}
		
		return super.getHeaders(headerName);
	}
		
	@Override
	public void removeHeader(String headerName) {
		if(!headersToParse.contains(headerName.toLowerCase())) {			
			delegate.removeHeaderNotParsed(headerName.toLowerCase());
		}
		super.removeHeader(headerName);
	}
	
	@Override
	public void removeHeader(String headerName, boolean top) {
		if(!headersToParse.contains(headerName.toLowerCase())) {
			delegate.removeHeaderNotParsed(headerName.toLowerCase());
		}
		super.removeHeader(headerName, top);
	}
	
	@Override
	public Object clone() {		
		SelectiveSIPResponse retval = (SelectiveSIPResponse) super.clone();
		retval.delegate = new SelectiveMessageDelegate();
		Map<String, String> headersNotParsed = delegate.getHeadersNotParsed();
		for(Entry<String, String> entry : headersNotParsed.entrySet()) {
			retval.delegate.addHeaderNotParsed(entry.getKey(), entry.getValue());
		}
		return retval;
	}
	
//	@Override
//	public void cleanUp() {
//		// let's encode the actual message into its string representation
//		stringRepresentation = encode();
//		// and nullify the rest
//		statusLine = null;
//		callIdHeader = null;
//		    	
//    	contentLengthHeader = null;
//    	cSeqHeader = null;
//    	forkId = null;
//    	fromHeader = null;
//    	if(headers != null) {
//    		headers.clear();
//    		headers = null;
//    	}
//    	matchExpression = null;
//    	maxForwardsHeader = null;
//    	messageContent = null;
//    	messageContentBytes = null;
//    	messageContentObject = null;
//    	if(headerTable != null) {
//    		headerTable.clear();
//    		headerTable = null;
//    	}
//    	stringRepresentation = null;
//    	toHeader = null;
//    	if(unrecognizedHeaders != null) {
//    		unrecognizedHeaders.clear();
//    		unrecognizedHeaders = null;
//    	}
//		super.cleanUp();
//	}
}
