/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
*
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement
*
* .
*
*/
package gov.nist.javax.sip.stack.timers;

import gov.nist.javax.sip.stack.SIPStackTimerTask;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Default SIP Timer implementation based on java.util.Timer 
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class DefaultSipTimer extends Timer implements SipTimer {

	private class DefaultTimerTask extends TimerTask {
		private SIPStackTimerTask task;

		public DefaultTimerTask(SIPStackTimerTask task) {
			this.task= task;
			task.setSipTimerTask(this);
		}
		
		public void run() {
			 try {
				 // task can be null if it has been cancelled
				 if(task != null) {
					 task.runTask();					 
				 }
	        } catch (Throwable e) {
	            System.out.println("SIP stack timer task failed due to exception:");
	            e.printStackTrace();
	        }
		}
		
		@Override
		public boolean cancel() {
			if(task != null) {
				task.cleanUpBeforeCancel();
				task = null;
			}
			return super.cancel();
		}
	}
	
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay) {		
		super.schedule(new DefaultTimerTask(task), delay);
		return true;
	}
	
	@Override
	public boolean scheduleAtFixedRate(SIPStackTimerTask task, long delay,
			long period) {
		super.schedule(new DefaultTimerTask(task), delay, period);
		return true;
	}
	
	@Override
	public boolean cancel(SIPStackTimerTask task) {
		return ((TimerTask)task.getSipTimerTask()).cancel();
	}

	@Override
	public void start(Properties configurationProperties) {
		// don't need the properties so nothing to see here
	}

	@Override
	public void stop() {
		cancel();		
	}


}
