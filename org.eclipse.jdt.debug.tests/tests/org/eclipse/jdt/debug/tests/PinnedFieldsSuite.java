/*******************************************************************************
 * Copyright (c) 2026 Hélios Gilles and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Hélios Gilles - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.debug.tests.ui.PinnedFieldsRealInputTests;
import org.eclipse.jdt.debug.tests.ui.PinnedFieldsViewTests;
import org.eclipse.jdt.debug.tests.variables.PinnedFieldsEdgeCaseTests;
import org.eclipse.jdt.debug.tests.variables.PinnedFieldsTests;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * CI-only suite running the "Pin to Top" tests.
 */
public class PinnedFieldsSuite extends DebugSuite {

	public static Test suite() {
		return new PinnedFieldsSuite();
	}

	public PinnedFieldsSuite() {
		addTest(new TestSuite(PinnedFieldsTests.class));
		addTest(new TestSuite(PinnedFieldsEdgeCaseTests.class));
		// repeated to detect unstable UI tests
		int repeat = Integer.parseInt(System.getenv().getOrDefault("PIN_REPEAT", "1"));
		for (int i = 0; i < repeat; i++) {
			addTest(new TestSuite(PinnedFieldsViewTests.class));
		}
		if (Platform.OS_LINUX.equals(Platform.getOS())) {
			// real input events are injected with xdotool
			addTest(new PinnedFieldsRealInputTests("testRealUserInteraction"));
		}
	}
}
