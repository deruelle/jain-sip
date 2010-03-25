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
package gov.nist.javax.sip.parser.selective;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SIPConstants;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.header.StatusLine;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.message.selective.SelectiveSIPRequest;
import gov.nist.javax.sip.message.selective.SelectiveSIPResponse;
import gov.nist.javax.sip.parser.ParseExceptionListener;
import gov.nist.javax.sip.parser.chars.CharsMsgParser;
import gov.nist.javax.sip.parser.chars.Lexer;
import gov.nist.javax.sip.parser.chars.RequestLineParser;
import gov.nist.javax.sip.parser.chars.StatusLineParser;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SelectiveCharParser extends CharsMsgParser {

	private static final String HEADERS_TO_PARSE = "gov.nist.java.sip.selective.parser.HEADERS_TO_PARSE"; 
	private static Set<String> headersToParse = new HashSet<String>();		
	
	StackLogger logger;
	
	public SelectiveCharParser(SIPTransactionStack stack, Properties configurationProperties) {
		logger = stack.getStackLogger();
		if(headersToParse.size() < 1) {
			boolean headersToParseFound = false;		
			if(configurationProperties != null) {
				String headersToParseString = configurationProperties.getProperty(HEADERS_TO_PARSE);
				if(headersToParseString != null) {
					headersToParse.clear();
					StringTokenizer stringTokenizer = new StringTokenizer(headersToParseString, ",");
					while (stringTokenizer.hasMoreTokens()) {
						String headerToParse = stringTokenizer.nextToken();
						headersToParse.add(headerToParse.trim().toLowerCase());					
					}
					headersToParseFound = true;
				}
			} 
			if(!headersToParseFound) {
				headersToParse.add(FromHeader.NAME.toLowerCase());
				headersToParse.add(ToHeader.NAME.toLowerCase());
				headersToParse.add(CSeqHeader.NAME.toLowerCase());
				headersToParse.add(CallIdHeader.NAME.toLowerCase());
				headersToParse.add(MaxForwardsHeader.NAME.toLowerCase());
				headersToParse.add(ViaHeader.NAME.toLowerCase());
				headersToParse.add(ContactHeader.NAME.toLowerCase());
				headersToParse.add(RecordRouteHeader.NAME.toLowerCase());
				headersToParse.add(RouteHeader.NAME.toLowerCase());
				headersToParse.add(ContentLengthHeader.NAME.toLowerCase());
				headersToParse.add(SubscriptionStateHeader.NAME.toLowerCase());
				headersToParse.add(EventHeader.NAME.toLowerCase());
			}
			if(logger.isLoggingEnabled()) {
				logger.logDebug("Headers to parse : ");
				for (String headerToParse : headersToParse) {
					logger.logDebug(headerToParse);
				}
			}
		}		
	}

	
	@Override
	public SIPMessage parseSIPMessage(byte[] msgBuffer, boolean readBody, boolean strict, ParseExceptionListener parseExceptionListener) throws ParseException {	
		return super.parseSIPMessage(msgBuffer, readBody, strict, parseExceptionListener);
	}
	
	@Override
	protected void processHeader(char[] header, SIPMessage message, ParseExceptionListener parseExceptionListener, byte[] msgBuffer)
			throws ParseException {
		String headerName = Lexer.getHeaderName(header);        
        if (headerName == null)
            throw new ParseException("The header name or value is null", 0);
        
		// logic to process headers only if they are present in the list of headers to parse from a given stack property
		if(headersToParse.contains(headerName.toLowerCase())) {
			super.processHeader(header, message, parseExceptionListener, msgBuffer);
		} else {
			((SelectiveMessage) message).addHeaderNotParsed(headerName, new String(header));
		}
	}
	
	@Override
	protected SIPMessage processFirstLine(char[] firstLine, ParseExceptionListener parseExceptionListener, byte[] msgBuffer)
			throws ParseException {
		return reprocessFirstLine(firstLine, null, parseExceptionListener, msgBuffer);		
	}
	
	protected SIPMessage reprocessFirstLine(char[] firstLine, SIPMessage sipMessage, ParseExceptionListener parseExceptionListener, byte[] msgBuffer) throws ParseException {
		SIPMessage message = sipMessage;
		char[] retval = new char[firstLine.length + 1];
        System.arraycopy(firstLine, 0, retval, 0, firstLine.length);
        retval[firstLine.length] = '\n';
        
        if (!new String(firstLine).startsWith(SIPConstants.SIP_VERSION_STRING)) {
        	if(message == null) {
        		message = new SelectiveSIPRequest(headersToParse);
        	}
            try {
                RequestLine requestLine = new RequestLineParser(retval)
                        .parse();
                ((SIPRequest) message).setRequestLine(requestLine);
            } catch (ParseException ex) {
                if (parseExceptionListener != null)
					try {
						parseExceptionListener.handleException(ex, message,
						        RequestLine.class, new String(retval), new String(msgBuffer, "UTF-8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				else
                    throw ex;

            }
        } else {
        	if(message == null) {
        		message = new SelectiveSIPResponse(headersToParse);
        	}
            try {
                StatusLine sl = new StatusLineParser(retval).parse();
                ((SIPResponse) message).setStatusLine(sl);
            } catch (ParseException ex) {
                if (parseExceptionListener != null) {
                    try {
						parseExceptionListener.handleException(ex, message,
						        StatusLine.class, new String(retval), new String(msgBuffer, "UTF-8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
                } else
                    throw ex;

            }
        }
        return message;
	}
}
