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
package org.eclipse.jdt.internal.debug.ui.variables;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;

/**
 * Unpins all the fields pinned to the top of the variables views.
 *
 * @see PinnedFieldsManager
 */
public class UnpinAllFieldsHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		PinnedFieldsManager.getDefault().unpinAll();
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(PinnedFieldsManager.getDefault().hasPinnedFields());
	}
}
