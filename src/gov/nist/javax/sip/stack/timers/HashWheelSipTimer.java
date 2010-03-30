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
package gov.nist.javax.sip.stack.timers;

import gov.nist.javax.sip.stack.SIPStackTimerTask;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

/**
 * Implementation of the SIP Timer based on Netty(http://www.jboss.org/netty)'s HashedWheelTimer 
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class HashWheelSipTimer implements SipTimer {
	HashedWheelTimer hashWheelTimer;
	
	public HashWheelSipTimer() {
		hashWheelTimer = new HashedWheelTimer(50, TimeUnit.MILLISECONDS, 1280);
	}
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	@Override
	public void stop() {
		hashWheelTimer.stop();
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay) {
		Timeout timeout = hashWheelTimer.newTimeout(new HashWheelTimerTask(task, -1), delay, TimeUnit.MILLISECONDS);
		task.setSipTimerTask(timeout);
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#scheduleAtFixedRate(gov.nist.javax.sip.stack.SIPStackTimerTask, long, long)
	 */
	@Override
	public boolean scheduleWithFixedDelay(SIPStackTimerTask task, long delay,
			long period) {
		Timeout timeout = hashWheelTimer.newTimeout(new HashWheelTimerTask(task, period), delay, TimeUnit.MILLISECONDS);
		task.setSipTimerTask(timeout);
		return true;
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#start(java.util.Properties)
	 */
	@Override
	public void start(Properties configurationProperties) {
		// could be used to set the number of thread for the executor
		hashWheelTimer.start();
	}

	@Override
	public boolean cancel(SIPStackTimerTask task) {
		boolean cancelled = true;
		Timeout sipTimerTask = (Timeout) task.getSipTimerTask();
		if(sipTimerTask != null) {
			task.cleanUpBeforeCancel();
			((HashWheelTimerTask)sipTimerTask.getTask()).cancel();
			task.setSipTimerTask(null);
			sipTimerTask.cancel();
		} 
		return cancelled;
	}

	private class HashWheelTimerTask implements TimerTask {
		private SIPStackTimerTask task;
		private long period;
		private boolean cancelled = false;
		
		public HashWheelTimerTask(SIPStackTimerTask task, long period) {
			this.task= task;
			this.period = period;
		}		

		public void cancel() {
			task = null;
			cancelled = true;			
			period = -1;
		}

		@Override
		public void run(Timeout timeout) throws Exception {
			try {				
				 // task can be null if it has been cancelled
				 if(task != null) {
					 task.runTask();
					 if(period > 0 && !cancelled) {
						 scheduleWithFixedDelay(task, period, period);
					 }
				 }
	        } catch (Throwable e) {
	            System.out.println("SIP stack timer task failed due to exception:");
	            e.printStackTrace();
	        }
		}
	}

}
