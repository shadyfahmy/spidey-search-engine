/*
 * Copyright 2009 Marc-Olaf Jaschke
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jkeylockmanager.manager.exception;

import java.util.concurrent.TimeUnit;

/**
 * Use this exception, if a thread has exceeded a timeout, while waiting for a
 * lock.
 * 
 * @author Marc-Olaf Jaschke
 * 
 */
public class KeyLockManagerTimeoutException extends KeyLockManagerException {

	private static final long serialVersionUID = -9202899074196960232L;

	private final long timeout;
	private final TimeUnit timeUnit;

	public KeyLockManagerTimeoutException(final long timeout, final TimeUnit timeUnit) {
		super(String.format("timed out after %d [%s] while trying to acquire lock", timeout, timeUnit));
		this.timeout = timeout;
		this.timeUnit = timeUnit;
	}

	public long getTimeout() {
		return timeout;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
}