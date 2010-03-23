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
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.parser.selective.SelectiveMessage;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.LinkedList;
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
public class SelectiveSIPRequest extends SIPRequest implements SelectiveMessage {
	
	SelectiveMessageDelegate delegate;
	private static Set<String> headersToParse;		

	public SelectiveSIPRequest(Set<String> headersToParse) {
		SelectiveSIPRequest.headersToParse = headersToParse;
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
        if (requestLine != null) {
            this.setRequestLineDefaults();
            retval = requestLine.encode() + encodeSelective();
        } else if (this.isNullRequest()) {
            retval = "\r\n\r\n";
        } else {       
            retval = encodeSelective();
        }
        return retval;
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
		if (this instanceof SIPRequest && ((SIPRequest) this).isNullRequest()) {
            return "\r\n\r\n".getBytes();
        }
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
		if (this.isNullRequest()) {
            // Encoding a null message for keepalive.
            return "\r\n\r\n".getBytes();
        } else if ( this.requestLine == null ) {
            return new byte[0];
        }

        byte[] rlbytes = null;
        if (requestLine != null) {
            try {
                rlbytes = requestLine.encode().getBytes("UTF-8");
            } catch (UnsupportedEncodingException ex) {
                InternalErrorHandler.handleException(ex);
            }
        }
        byte[] superbytes = encodeAsBytesSelective(transport);
        byte[] retval = new byte[rlbytes.length + superbytes.length];
        System.arraycopy(rlbytes, 0, retval, 0, rlbytes.length);
        System.arraycopy(superbytes, 0, retval, rlbytes.length, superbytes.length);
        return retval;
	}
	
	@Override
	public StringBuilder encodeMessage(StringBuilder retval) {
		 if (requestLine != null) {
            this.setRequestLineDefaults();
            requestLine.encode(retval);
            encodeSIPHeaders(retval);
        } else if (this.isNullRequest()) {
            retval.append("\r\n\r\n");
        } else
            retval = encodeSIPHeaders(retval);
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
		SelectiveSIPRequest retval = (SelectiveSIPRequest) super.clone();
		retval.delegate = new SelectiveMessageDelegate();
		Map<String, String> headersNotParsed = delegate.getHeadersNotParsed();
		for(Entry<String, String> entry : headersNotParsed.entrySet()) {
			retval.delegate.addHeaderNotParsed(entry.getKey(), entry.getValue());
		}
		return retval;
	}
	
//	Semaphore cleanUpSem = new Semaphore(1);
//	boolean isCleanedUp = false;
//	String savedMessageAsString = null;
//	String mergedId;
//	String method;
//	String fromTag;
//	String toTag;
//	Via topMostViaHeader = null;	
//	List<String> savedHeadersForResponse = new ArrayList<String>(0); 
//	static String[] headerNamesForResponse = new String[] {FromHeader.NAME.toLowerCase(), ToHeader.NAME.toLowerCase(), ViaHeader.NAME.toLowerCase(), CallIdHeader.NAME.toLowerCase(), RecordRouteHeader.NAME.toLowerCase(), CSeqHeader.NAME.toLowerCase(), TimeStampHeader.NAME};
//	
//	@Override
//	public synchronized void cleanUp() {
//		// let's encode the actual message into its string representation
//		try {
//			if(!isCleanedUp) {		
//				cleanUpSem.acquire();
//				savedMessageAsString = super.encode();
//				mergedId = super.getMergeId();
//				method = super.getMethod();	
//				fromTag = super.getFromTag();
//				toTag = super.getToTag();
//				ViaList viaList = super.getViaHeaders();
//				if (viaList != null)
//		            topMostViaHeader = (Via) viaList.getFirst();
//				// and nullify the rest
//				super.setRequestLine(null);
//		        for (String headerNameForResponse : headerNamesForResponse) {
//		        	SIPHeader header = headerTable.get(headerNameForResponse);
//		        	if(header != null) {
//		        		savedHeadersForResponse.add(header.toString());
//		        	}
//				}
//				isCleanedUp = true;
//				callIdHeader = null;
//		    	contentLengthHeader = null;
//		    	cSeqHeader = null;
//		    	forkId = null;
//		    	fromHeader = null;
//		    	if(headers != null) {
//		    		headers.clear();
//	//	    		headers = null;
//		    	}
//		    	matchExpression = null;
//		    	maxForwardsHeader = null;
//		    	messageContent = null;
//		    	messageContentBytes = null;
//		    	messageContentObject = null;
//		    	if(headerTable != null) {
//		    		headerTable.clear();
//	//	    		headerTable = null;
//		    	}
//		    	stringRepresentation = null;
//		    	toHeader = null;
//		    	if(unrecognizedHeaders != null) {
//		    		unrecognizedHeaders.clear();
//	//	    		unrecognizedHeaders = null;
//		    	}
//				super.cleanUp();
//				cleanUpSem.release();
//			}			
//		} catch (InterruptedException e) {}
//	}
//
//	private synchronized void reparseMessage() {
//		try {			
//			if(isCleanedUp) {
//				cleanUpSem.acquire();
//				isCleanedUp = false;
//				try {
//					new SelectiveParser().reparseSIPMessage(savedMessageAsString, this);
//					savedMessageAsString = null;
//					mergedId = null;
//					method = null;
//					toTag = null;
//					fromTag = null;		
//					topMostViaHeader = null;
//					savedHeadersForResponse.clear();
//				} catch (ParseException e) {
//					throw new IllegalArgumentException("A PArsing problem occured while reparsing the message " + savedMessageAsString, e);
//				} finally {
//					cleanUpSem.release();
//				}
//			}			
//		} catch (InterruptedException e) {}
//	}
//	
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#checkHeaders()
//	 */
//	@Override
//	public void checkHeaders() throws ParseException {
//		reparseMessage();
//		super.checkHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#clone()
//	 */
//	@Override
//	public Object clone() {
//		reparseMessage();
//		return super.clone();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createACKRequest()
//	 */
//	@Override
//	public SIPRequest createACKRequest() {
//		reparseMessage();
//		return super.createACKRequest();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createAckRequest(gov.nist.javax.sip.header.To)
//	 */
//	@Override
//	public SIPRequest createAckRequest(To responseToHeader) {
//		reparseMessage();
//		return super.createAckRequest(responseToHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createBYERequest(boolean)
//	 */
//	@Override
//	public SIPRequest createBYERequest(boolean switchHeaders) {
//		reparseMessage();
//		return super.createBYERequest(switchHeaders);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createCancelRequest()
//	 */
//	@Override
//	public SIPRequest createCancelRequest() throws SipException {
//		reparseMessage();
//		return super.createCancelRequest();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createResponse(int)
//	 */
//	@Override
//	public SIPResponse createResponse(int statusCode) {
//		return super.createResponse(statusCode);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createResponse(int, java.lang.String)
//	 */
//	@Override
//	public SIPResponse createResponse(int statusCode, String reasonPhrase) {
//		if(isCleanedUp) {
//			try {
//				cleanUpSem.acquire();
//				SIPResponse newResponse = new SIPResponse();
//		        try {
//		            newResponse.setStatusCode(statusCode);
//		        } catch (ParseException ex) {
//		            throw new IllegalArgumentException("Bad code " + statusCode);
//		        }
//		        if (reasonPhrase != null)
//		            newResponse.setReasonPhrase(reasonPhrase);
//		        else
//		            newResponse.setReasonPhrase(SIPResponse.getReasonPhrase(statusCode));
//		        for (String savedHeaderForResponse : savedHeadersForResponse) {
//		                try {
//		                    newResponse.attachHeader((SIPHeader) StringMsgParser.parseSIPHeader(savedHeaderForResponse), false);
//		                } catch (SIPDuplicateHeaderException e) {
//		                    e.printStackTrace();
//		                } catch (ParseException e) {
//		                	throw new IllegalArgumentException("A Parsing problem occured while reparsing the header " + savedHeaderForResponse, e);
//		                }
//		        }
//		        if (MessageFactoryImpl.getDefaultServerHeader() != null) {
//		            newResponse.setHeader(MessageFactoryImpl.getDefaultServerHeader());
//		
//		        }
//		        if (newResponse.getStatusCode() == 100) {
//		            // Trying is never supposed to have the tag parameter set.
//		            newResponse.getTo().removeParameter("tag");
//		
//		        }
//		        ServerHeader server = MessageFactoryImpl.getDefaultServerHeader();
//		        if (server != null) {
//		            newResponse.setHeader(server);
//		        }
//		        return newResponse;
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//				return null;
//			} finally {
//				cleanUpSem.release();
//			}
//		} else {
//			return super.createResponse(statusCode, reasonPhrase);
//		}
//		
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#createSIPRequest(gov.nist.javax.sip.header.RequestLine, boolean)
//	 */
//	@Override
//	public SIPRequest createSIPRequest(RequestLine requestLine,
//			boolean switchHeaders) {
//		reparseMessage();
//		return super.createSIPRequest(requestLine, switchHeaders);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#debugDump()
//	 */
//	@Override
//	public String debugDump() {
//		reparseMessage();
//		return super.debugDump();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#encode()
//	 */
//	@Override
//	public String encode() {
//		if(savedMessageAsString !=null) {
//			return savedMessageAsString;
//		}
//		return super.encode();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#encodeAsBytes(java.lang.String)
//	 */
//	@Override
//	public byte[] encodeAsBytes(String transport) {
//		if(savedMessageAsString !=null) {
//			return savedMessageAsString.getBytes();
//		}
//		return super.encodeAsBytes(transport);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#encodeMessage(java.lang.StringBuilder)
//	 */
//	@Override
//	public StringBuilder encodeMessage(StringBuilder retval) {
//		if(savedMessageAsString !=null) {
//			return retval.append(savedMessageAsString);
//		}
//		return super.encodeMessage(retval);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#equals(java.lang.Object)
//	 */
//	@Override
//	public boolean equals(Object other) {
//		reparseMessage();
//		return super.equals(other);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getDialogId(boolean)
//	 */
//	@Override
//	public String getDialogId(boolean isServer) {
//		reparseMessage();
//		return super.getDialogId(isServer);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getDialogId(boolean, java.lang.String)
//	 */
//	@Override
//	public String getDialogId(boolean isServer, String toTag) {
//		reparseMessage();
//		return super.getDialogId(isServer, toTag);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getFirstLine()
//	 */
//	@Override
//	public String getFirstLine() {
//		reparseMessage();
//		return super.getFirstLine();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getInviteTransaction()
//	 */
//	@Override
//	public Object getInviteTransaction() {
//		reparseMessage();
//		return super.getInviteTransaction();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getMergeId()
//	 */
//	@Override
//	public String getMergeId() {
//		if(mergedId != null) {
//			return mergedId;
//		}
//		return super.getMergeId();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getMessageAsEncodedStrings()
//	 */
//	@Override
//	public LinkedList getMessageAsEncodedStrings() {
//		reparseMessage();
//		return super.getMessageAsEncodedStrings();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getMessageChannel()
//	 */
//	@Override
//	public Object getMessageChannel() {
//		reparseMessage();
//		return super.getMessageChannel();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getMethod()
//	 */
//	@Override
//	public String getMethod() {
//		if(method != null) {
//			return method;
//		}
//		return super.getMethod();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getRequestLine()
//	 */
//	@Override
//	public RequestLine getRequestLine() {
//		reparseMessage();
//		return super.getRequestLine();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getRequestURI()
//	 */
//	@Override
//	public URI getRequestURI() {
//		reparseMessage();
//		return super.getRequestURI();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getSIPVersion()
//	 */
//	@Override
//	public String getSIPVersion() {
//		reparseMessage();
//		return super.getSIPVersion();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getTransaction()
//	 */
//	@Override
//	public Object getTransaction() {
//		reparseMessage();
//		return super.getTransaction();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getViaHost()
//	 */
//	@Override
//	public String getViaHost() {
//		reparseMessage();
//		return super.getViaHost();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#getViaPort()
//	 */
//	@Override
//	public int getViaPort() {
//		reparseMessage();
//		return super.getViaPort();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#match(java.lang.Object)
//	 */
//	@Override
//	public boolean match(Object matchObj) {
//		reparseMessage();
//		return super.match(matchObj);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setDefaults()
//	 */
//	@Override
//	protected void setDefaults() {
//		reparseMessage();
//		super.setDefaults();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setMethod(java.lang.String)
//	 */
//	@Override
//	public void setMethod(String method) {
//		reparseMessage();
//		super.setMethod(method);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setRequestLine(gov.nist.javax.sip.header.RequestLine)
//	 */
//	@Override
//	public void setRequestLine(RequestLine requestLine) {
//		reparseMessage();
//		super.setRequestLine(requestLine);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setRequestLineDefaults()
//	 */
//	@Override
//	protected void setRequestLineDefaults() {
//		reparseMessage();
//		super.setRequestLineDefaults();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setRequestURI(javax.sip.address.URI)
//	 */
//	@Override
//	public void setRequestURI(URI uri) {
//		reparseMessage();
//		super.setRequestURI(uri);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#setSIPVersion(java.lang.String)
//	 */
//	@Override
//	public void setSIPVersion(String sipVersion) throws ParseException {
//		reparseMessage();
//		super.setSIPVersion(sipVersion);
//	}
//	
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPRequest#toString()
//	 */
//	@Override
//	public String toString() {
//		if(savedMessageAsString != null) {
//			return savedMessageAsString;
//		}
//		return super.toString();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#addFirst(javax.sip.header.Header)
//	 */
//	@Override
//	public void addFirst(Header header) throws SipException,
//			NullPointerException {
//		reparseMessage();
//		super.addFirst(header);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#addHeader(javax.sip.header.Header)
//	 */
//	@Override
//	public void addHeader(Header sipHeader) {
//		reparseMessage();
//		super.addHeader(sipHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#addHeader(java.lang.String)
//	 */
//	@Override
//	public void addHeader(String sipHeader) {
//		reparseMessage();
//		super.addHeader(sipHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#addLast(javax.sip.header.Header)
//	 */
//	@Override
//	public void addLast(Header header) throws SipException,
//			NullPointerException {
//		reparseMessage();
//		super.addLast(header);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#addUnparsed(java.lang.String)
//	 */
//	@Override
//	public void addUnparsed(String unparsed) {
//		reparseMessage();
//		super.addUnparsed(unparsed);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#attachHeader(gov.nist.javax.sip.header.SIPHeader, boolean)
//	 */
//	@Override
//	public void attachHeader(SIPHeader h, boolean replaceflag)
//			throws SIPDuplicateHeaderException {
//		reparseMessage();
//		super.attachHeader(h, replaceflag);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#attachHeader(gov.nist.javax.sip.header.SIPHeader, boolean, boolean)
//	 */
//	@Override
//	public void attachHeader(SIPHeader header, boolean replaceFlag, boolean top)
//			throws SIPDuplicateHeaderException {
//		reparseMessage();
//		super.attachHeader(header, replaceFlag, top);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#encodeSIPHeaders(java.lang.StringBuilder)
//	 */
//	@Override
//	protected StringBuilder encodeSIPHeaders(StringBuilder encoding) {
//		reparseMessage();
//		return super.encodeSIPHeaders(encoding);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getApplicationData()
//	 */
//	@Override
//	public Object getApplicationData() {
//		reparseMessage();
//		return super.getApplicationData();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getAuthorization()
//	 */
//	@Override
//	public Authorization getAuthorization() {
//		reparseMessage();
//		return super.getAuthorization();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getCSeq()
//	 */
//	@Override
//	public CSeqHeader getCSeq() {
//		reparseMessage();
//		return super.getCSeq();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getCSeqHeader()
//	 */
//	@Override
//	public CSeqHeader getCSeqHeader() {
//		reparseMessage();
//		return super.getCSeqHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getCallId()
//	 */
//	@Override
//	public CallIdHeader getCallId() {
//		reparseMessage();
//		return super.getCallId();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getCallIdHeader()
//	 */
//	@Override
//	public CallIdHeader getCallIdHeader() {
//		reparseMessage();
//		return super.getCallIdHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContactHeader()
//	 */
//	@Override
//	public Contact getContactHeader() {
//		reparseMessage();
//		return super.getContactHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContactHeaders()
//	 */
//	@Override
//	public ContactList getContactHeaders() {
//		reparseMessage();
//		return super.getContactHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContent()
//	 */
//	@Override
//	public Object getContent() {
//		reparseMessage();
//		return super.getContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentDisposition()
//	 */
//	@Override
//	public ContentDispositionHeader getContentDisposition() {
//		reparseMessage();
//		return super.getContentDisposition();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentEncoding()
//	 */
//	@Override
//	public ContentEncodingHeader getContentEncoding() {
//		reparseMessage();
//		return super.getContentEncoding();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentLanguage()
//	 */
//	@Override
//	public ContentLanguageHeader getContentLanguage() {
//		reparseMessage();
//		return super.getContentLanguage();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentLength()
//	 */
//	@Override
//	public ContentLengthHeader getContentLength() {
//		reparseMessage();
//		return super.getContentLength();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentLengthHeader()
//	 */
//	@Override
//	public ContentLengthHeader getContentLengthHeader() {
//		reparseMessage();
//		return super.getContentLengthHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getContentTypeHeader()
//	 */
//	@Override
//	public ContentType getContentTypeHeader() {
//		reparseMessage();
//		return super.getContentTypeHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getErrorInfoHeaders()
//	 */
//	@Override
//	public ErrorInfoList getErrorInfoHeaders() {
//		reparseMessage();
//		return super.getErrorInfoHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getExpires()
//	 */
//	@Override
//	public ExpiresHeader getExpires() {
//		reparseMessage();
//		return super.getExpires();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getForkId()
//	 */
//	@Override
//	public String getForkId() {
//		reparseMessage();
//		return super.getForkId();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getFrom()
//	 */
//	@Override
//	public FromHeader getFrom() {
//		reparseMessage();
//		return super.getFrom();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getFromHeader()
//	 */
//	@Override
//	public FromHeader getFromHeader() {
//		reparseMessage();
//		return super.getFromHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getFromTag()
//	 */
//	@Override
//	public String getFromTag() {
//		if(fromTag != null) {
//			return fromTag;
//		}
//		return super.getFromTag();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeader(java.lang.String)
//	 */
//	@Override
//	public Header getHeader(String headerName) {
//		reparseMessage();
//		return super.getHeader(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeaderAsFormattedString(java.lang.String)
//	 */
//	@Override
//	public String getHeaderAsFormattedString(String name) {
//		reparseMessage();
//		return super.getHeaderAsFormattedString(name);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeaderNames()
//	 */
//	@Override
//	public ListIterator<String> getHeaderNames() {
//		reparseMessage();
//		return super.getHeaderNames();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeaders()
//	 */
//	@Override
//	public Iterator<SIPHeader> getHeaders() {
//		reparseMessage();
//		return super.getHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeaders(java.lang.String)
//	 */
//	@Override
//	public ListIterator<SIPHeader> getHeaders(String headerName) {
//		reparseMessage();
//		return super.getHeaders(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getHeadersAsCollection()
//	 */
//	@Override
//	public Collection<SIPHeader> getHeadersAsCollection() {
//		reparseMessage();
//		return super.getHeadersAsCollection();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getMaxForwards()
//	 */
//	@Override
//	public MaxForwardsHeader getMaxForwards() {
//		reparseMessage();
//		return super.getMaxForwards();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getMessageContent()
//	 */
//	@Override
//	public String getMessageContent() throws UnsupportedEncodingException {
//		reparseMessage();
//		return super.getMessageContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getMultipartMimeContent()
//	 */
//	@Override
//	public MultipartMimeContent getMultipartMimeContent() throws ParseException {
//		reparseMessage();
//		return super.getMultipartMimeContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getRawContent()
//	 */
//	@Override
//	public byte[] getRawContent() {
//		reparseMessage();
//		return super.getRawContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getRecordRouteHeaders()
//	 */
//	@Override
//	public RecordRouteList getRecordRouteHeaders() {
//		reparseMessage();
//		return super.getRecordRouteHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getRouteHeaders()
//	 */
//	@Override
//	public RouteList getRouteHeaders() {
//		reparseMessage();
//		return super.getRouteHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getSIPHeaderListLowerCase(java.lang.String)
//	 */
//	@Override
//	protected SIPHeader getSIPHeaderListLowerCase(String lowerCaseHeaderName) {
//		reparseMessage();
//		return super.getSIPHeaderListLowerCase(lowerCaseHeaderName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getSize()
//	 */
//	@Override
//	public int getSize() {
//		reparseMessage();
//		return super.getSize();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getTo()
//	 */
//	@Override
//	public ToHeader getTo() {
//		reparseMessage();
//		return super.getTo();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getToHeader()
//	 */
//	@Override
//	public ToHeader getToHeader() {
//		reparseMessage();
//		return super.getToHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getToTag()
//	 */
//	@Override
//	public String getToTag() {
//		if(toTag != null) {
//			return toTag;
//		}
//		return super.getToTag();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getTopmostVia()
//	 */
//	@Override
//	public Via getTopmostVia() {
//		if(isCleanedUp) {			
//			return topMostViaHeader;
//		}
//		return super.getTopmostVia();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getTopmostViaHeader()
//	 */
//	@Override
//	public ViaHeader getTopmostViaHeader() {
//		if(isCleanedUp) {			
//			return topMostViaHeader;
//		}
//		return super.getTopmostViaHeader();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getTransactionId()
//	 */
//	@Override
//	public String getTransactionId() {
//		reparseMessage();
//		return super.getTransactionId();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getUnrecognizedHeaders()
//	 */
//	@Override
//	public ListIterator<String> getUnrecognizedHeaders() {
//		reparseMessage();
//		return super.getUnrecognizedHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getUnrecognizedHeadersList()
//	 */
//	@Override
//	protected LinkedList<String> getUnrecognizedHeadersList() {
//		reparseMessage();
//		return super.getUnrecognizedHeadersList();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#getViaHeaders()
//	 */
//	@Override
//	public ViaList getViaHeaders() {
//		reparseMessage();
//		return super.getViaHeaders();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#hasContent()
//	 */
//	@Override
//	public boolean hasContent() {
//		reparseMessage();
//		return super.hasContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#hasFromTag()
//	 */
//	@Override
//	public boolean hasFromTag() {
//		reparseMessage();
//		return super.hasFromTag();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#hasHeader(java.lang.String)
//	 */
//	@Override
//	public boolean hasHeader(String headerName) {
//		reparseMessage();
//		return super.hasHeader(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#hasToTag()
//	 */
//	@Override
//	public boolean hasToTag() {
//		reparseMessage();
//		return super.hasToTag();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#hashCode()
//	 */
//	@Override
//	public int hashCode() {
//		reparseMessage();
//		return super.hashCode();
//	}
//
////	/* (non-Javadoc)
////	 * @see gov.nist.javax.sip.message.SIPMessage#isNullRequest()
////	 */
////	@Override
////	public boolean isNullRequest() {
////		reparseMessage();
////		return super.isNullRequest();
////	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#merge(java.lang.Object)
//	 */
//	@Override
//	public void merge(Object template) {
//		reparseMessage();
//		super.merge(template);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#removeContent()
//	 */
//	@Override
//	public void removeContent() {
//		reparseMessage();
//		super.removeContent();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#removeFirst(java.lang.String)
//	 */
//	@Override
//	public void removeFirst(String headerName) throws NullPointerException {
//		reparseMessage();
//		super.removeFirst(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#removeHeader(java.lang.String, boolean)
//	 */
//	@Override
//	public void removeHeader(String headerName, boolean top) {
//		reparseMessage();
//		super.removeHeader(headerName, top);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#removeHeader(java.lang.String)
//	 */
//	@Override
//	public void removeHeader(String headerName) {
//		reparseMessage();
//		super.removeHeader(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#removeLast(java.lang.String)
//	 */
//	@Override
//	public void removeLast(String headerName) {
//		reparseMessage();
//		super.removeLast(headerName);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setApplicationData(java.lang.Object)
//	 */
//	@Override
//	public void setApplicationData(Object applicationData) {
//		reparseMessage();
//		super.setApplicationData(applicationData);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setCSeq(javax.sip.header.CSeqHeader)
//	 */
//	@Override
//	public void setCSeq(CSeqHeader cseqHeader) {
//		reparseMessage();
//		super.setCSeq(cseqHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setCallId(javax.sip.header.CallIdHeader)
//	 */
//	@Override
//	public void setCallId(CallIdHeader callId) {
//		reparseMessage();
//		super.setCallId(callId);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setCallId(java.lang.String)
//	 */
//	@Override
//	public void setCallId(String callId) throws ParseException {
//		reparseMessage();
//		super.setCallId(callId);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setContent(java.lang.Object, javax.sip.header.ContentTypeHeader)
//	 */
//	@Override
//	public void setContent(Object content, ContentTypeHeader contentTypeHeader)
//			throws ParseException {
//		reparseMessage();
//		super.setContent(content, contentTypeHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setContentDisposition(javax.sip.header.ContentDispositionHeader)
//	 */
//	@Override
//	public void setContentDisposition(
//			ContentDispositionHeader contentDispositionHeader) {
//		reparseMessage();
//		super.setContentDisposition(contentDispositionHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setContentEncoding(javax.sip.header.ContentEncodingHeader)
//	 */
//	@Override
//	public void setContentEncoding(ContentEncodingHeader contentEncodingHeader) {
//		reparseMessage();
//		super.setContentEncoding(contentEncodingHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setContentLanguage(javax.sip.header.ContentLanguageHeader)
//	 */
//	@Override
//	public void setContentLanguage(ContentLanguageHeader contentLanguageHeader) {
//		reparseMessage();
//		super.setContentLanguage(contentLanguageHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setContentLength(javax.sip.header.ContentLengthHeader)
//	 */
//	@Override
//	public void setContentLength(ContentLengthHeader contentLength) {
//		reparseMessage();
//		super.setContentLength(contentLength);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setExpires(javax.sip.header.ExpiresHeader)
//	 */
//	@Override
//	public void setExpires(ExpiresHeader expiresHeader) {
//		reparseMessage();
//		super.setExpires(expiresHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setFrom(javax.sip.header.FromHeader)
//	 */
//	@Override
//	public void setFrom(FromHeader from) {
//		reparseMessage();
//		super.setFrom(from);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setFromTag(java.lang.String)
//	 */
//	@Override
//	public void setFromTag(String tag) {
//		reparseMessage();
//		super.setFromTag(tag);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setHeader(javax.sip.header.Header)
//	 */
//	@Override
//	public void setHeader(Header sipHeader) {
//		reparseMessage();
//		super.setHeader(sipHeader);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setHeader(gov.nist.javax.sip.header.SIPHeaderList)
//	 */
//	@Override
//	public void setHeader(SIPHeaderList<Via> sipHeaderList) {
//		reparseMessage();
//		super.setHeader(sipHeaderList);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setHeaders(java.util.List)
//	 */
//	@Override
//	public void setHeaders(List<SIPHeader> headers) {
//		reparseMessage();
//		super.setHeaders(headers);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMaxForwards(javax.sip.header.MaxForwardsHeader)
//	 */
//	@Override
//	public void setMaxForwards(MaxForwardsHeader maxForwards) {
//		reparseMessage();
//		super.setMaxForwards(maxForwards);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMessageContent(java.lang.String, java.lang.String, java.lang.String)
//	 */
//	@Override
//	public void setMessageContent(String type, String subType,
//			String messageContent) {
//		reparseMessage();
//		super.setMessageContent(type, subType, messageContent);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMessageContent(java.lang.String, java.lang.String, byte[])
//	 */
//	@Override
//	public void setMessageContent(String type, String subType,
//			byte[] messageContent) {
//		reparseMessage();
//		super.setMessageContent(type, subType, messageContent);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMessageContent(java.lang.String, boolean, boolean, int)
//	 */
//	@Override
//	public void setMessageContent(String content, boolean strict,
//			boolean computeContentLength, int givenLength)
//			throws ParseException {
//		reparseMessage();
//		super.setMessageContent(content, strict, computeContentLength, givenLength);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMessageContent(byte[])
//	 */
//	@Override
//	public void setMessageContent(byte[] content) {
//		reparseMessage();
//		super.setMessageContent(content);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setMessageContent(byte[], boolean, int)
//	 */
//	@Override
//	public void setMessageContent(byte[] content, boolean computeContentLength,
//			int givenLength) throws ParseException {
//		reparseMessage();
//		super.setMessageContent(content, computeContentLength, givenLength);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setNullRequest()
//	 */
//	@Override
//	public void setNullRequest() {
//		reparseMessage();
//		super.setNullRequest();
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setSize(int)
//	 */
//	@Override
//	public void setSize(int size) {
//		reparseMessage();
//		super.setSize(size);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setTo(javax.sip.header.ToHeader)
//	 */
//	@Override
//	public void setTo(ToHeader to) {
//		reparseMessage();
//		super.setTo(to);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setToTag(java.lang.String)
//	 */
//	@Override
//	public void setToTag(String tag) {
//		reparseMessage();
//		super.setToTag(tag);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setUnrecognizedHeadersList(java.util.LinkedList)
//	 */
//	@Override
//	protected void setUnrecognizedHeadersList(
//			LinkedList<String> unrecognizedHeaders) {
//		reparseMessage();
//		super.setUnrecognizedHeadersList(unrecognizedHeaders);
//	}
//
//	/* (non-Javadoc)
//	 * @see gov.nist.javax.sip.message.SIPMessage#setVia(java.util.List)
//	 */
//	@Override
//	public void setVia(List viaList) {
//		reparseMessage();
//		super.setVia(viaList);
//	}
	
	
}
