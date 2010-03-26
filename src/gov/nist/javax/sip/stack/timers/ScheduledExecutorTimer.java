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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class ScheduledExecutorTimer implements SipTimer {

	ScheduledThreadPoolExecutor threadPoolExecutor;
	
	public ScheduledExecutorTimer() {
		threadPoolExecutor = new ScheduledThreadPoolExecutor(1);
		threadPoolExecutor.prestartAllCoreThreads();
	}
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	@Override
	public void stop() {
		threadPoolExecutor.shutdown();
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay) {
		ScheduledFuture<?> future = threadPoolExecutor.schedule(new ScheduledSipTimerTask(task), delay, TimeUnit.MILLISECONDS);
		task.setSipTimerTask((Runnable)future);
		return true;
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long, long)
	 */
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay, long period) {
		ScheduledFuture<?> future = threadPoolExecutor.scheduleWithFixedDelay(new ScheduledSipTimerTask(task), delay, period, TimeUnit.MILLISECONDS);
		task.setSipTimerTask((Runnable)future);
		return true;
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#setConfigurationProperties(java.util.Properties)
	 */
	@Override
	public void setConfigurationProperties(Properties configurationProperties) {
		// could be used to set the number of thread for the executor

	}

	@Override
	public boolean cancel(SIPStackTimerTask task) {
		Runnable sipTimerTask = (Runnable) task.getSipTimerTask();
		if(sipTimerTask != null) {
			task.cleanUpBeforeCancel();
			threadPoolExecutor.remove((Runnable)sipTimerTask);
			task.setSipTimerTask(null);
			return ((ScheduledFuture<?>) sipTimerTask).cancel(false);
		} else {
			return false;
		}
	}

	private class ScheduledSipTimerTask implements Runnable {
		private SIPStackTimerTask task;

		public ScheduledSipTimerTask(SIPStackTimerTask task) {
			this.task= task;			
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
	}
	
}
