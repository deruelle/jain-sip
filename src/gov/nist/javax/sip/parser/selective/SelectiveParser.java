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
import gov.nist.javax.sip.parser.Lexer;
import gov.nist.javax.sip.parser.RequestLineParser;
import gov.nist.javax.sip.parser.StatusLineParser;
import gov.nist.javax.sip.parser.StringMsgParser;
import gov.nist.javax.sip.stack.SIPTransactionStack;

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
public class SelectiveParser extends StringMsgParser {

	private static final String HEADERS_TO_PARSE = "gov.nist.java.sip.selective.parser.HEADERS_TO_PARSE"; 
	private static Set<String> headersToParse = new HashSet<String>();		
	
	StackLogger logger;
	
	public SelectiveParser(SIPTransactionStack stack, Properties configurationProperties) {
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
	public SIPMessage parseSIPMessage(byte[] msgBuffer) throws ParseException {	
		return super.parseSIPMessage(msgBuffer);
	}
	
	@Override
	protected void processHeader(String header, SIPMessage message)
			throws ParseException {
		String headerName = Lexer.getHeaderName(header);        
        if (headerName == null)
            throw new ParseException("The header name or value is null", 0);
        
		// logic to process headers only if they are present in the list of headers to parse from a given stack property
		if(headersToParse.contains(headerName.toLowerCase())) {
			super.processHeader(header, message);
		} else {
			((SelectiveMessage) message).addHeaderNotParsed(headerName, header);
		}
	}
	
	@Override
	protected SIPMessage processFirstLine(String firstLine)
			throws ParseException {
		return reprocessFirstLine(firstLine, null);		
	}
	
	protected SIPMessage reprocessFirstLine(String firstLine, SIPMessage sipMessage) throws ParseException {
		SIPMessage message = sipMessage;
        if (!firstLine.startsWith(SIPConstants.SIP_VERSION_STRING)) {
        	if(message == null) {
        		message = new SelectiveSIPRequest(headersToParse);
        	}
            try {
                RequestLine requestLine = new RequestLineParser(firstLine + "\n")
                        .parse();
                ((SIPRequest) message).setRequestLine(requestLine);
            } catch (ParseException ex) {
                if (this.parseExceptionListener != null)
                    this.parseExceptionListener.handleException(ex, message,
                            RequestLine.class, firstLine, rawStringMessage);
                else
                    throw ex;

            }
        } else {
        	if(message == null) {
        		message = new SelectiveSIPResponse(headersToParse);
        	}
            try {
                StatusLine sl = new StatusLineParser(firstLine + "\n").parse();
                ((SIPResponse) message).setStatusLine(sl);
            } catch (ParseException ex) {
                if (this.parseExceptionListener != null) {
                    this.parseExceptionListener.handleException(ex, message,
                            StatusLine.class, firstLine, rawStringMessage);
                } else
                    throw ex;

            }
        }
        return message;
	}

	public void reparseSIPMessage(String msgString, SIPMessage sipMessage) throws ParseException {
        if (msgString == null || msgString.length() == 0)
            return ;

        rawStringMessage = msgString;

        int i = 0;

        // Squeeze out any leading control character.
        try {
            while (msgString.charAt(i) < 0x20)
                i++;
        }
        catch (ArrayIndexOutOfBoundsException e) {
            // Array contains only control char, return null.
            return;
        } catch (StringIndexOutOfBoundsException ex) {
            return;
        }

        // Iterate thru the request/status line and headers.
        String currentLine = null;
        String currentHeader = null;
        boolean isFirstLine = true;
        
        do
        {
            int lineStart = i;

            // Find the length of the line.
            try {
                char c = msgString.charAt(i);
                while (c != '\r' && c != '\n')
                    c = msgString.charAt(++i);
            }
            catch (ArrayIndexOutOfBoundsException e) {
                // End of the message.
                break;
            } catch ( StringIndexOutOfBoundsException ex) {
                break;
            }

            // Make it a String.
            currentLine = msgString.substring(lineStart, i);
            currentLine = trimEndOfLine(currentLine);

            if (currentLine.length() == 0) {
                // Last header line, process the previous buffered header.
                if (currentHeader != null) {
                    processHeader(currentHeader, sipMessage);
                }
            }
            else {
                if (isFirstLine) {
                    reprocessFirstLine(currentLine, sipMessage);
                } else {
                    char firstChar = currentLine.charAt(0);
                    if (firstChar == '\t' || firstChar == ' ') {
                        if (currentHeader == null)
                            throw new ParseException("Bad header continuation.", 0);

                        // This is a continuation, append it to the previous line.
                        currentHeader += currentLine.substring(1);
                    }
                    else {
                        if (currentHeader != null) {
                            processHeader(currentHeader, sipMessage);
                        }
                        currentHeader = currentLine;
                    }
                }
            }

            if (msgString.charAt(i) == '\r' && msgString.length() > i+1 && msgString.charAt(i+1) == '\n')
                i++;

            i++;

            isFirstLine = false;
        }
        while (currentLine.length() > 0);

        sipMessage.setSize(i);

        // Check for content legth header
        if (readBody && sipMessage.getContentLength() != null ) {
            if ( sipMessage.getContentLength().getContentLength() != 0) {
                String body = msgString.substring(i);
                sipMessage.setMessageContent(body,this.strict,computeContentLengthFromMessage,sipMessage.getContentLength().getContentLength());
             } else if (!computeContentLengthFromMessage && sipMessage.getContentLength().getContentLength() == 0 && !msgString.endsWith("\r\n\r\n") ){
                 if ( strict ) {
                     throw new ParseException("Extraneous characters at the end of the message ",i);
                 }
             } 

        }
    }

}
