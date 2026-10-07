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
package org.eclipse.jdt.debug.tests.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IExpressionManager;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.core.model.IWatchExpression;
import org.eclipse.debug.internal.ui.viewers.model.provisional.PresentationContext;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.IDebugView;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.internal.debug.ui.variables.JavaContentProviderFilter;
import org.eclipse.jdt.internal.debug.ui.variables.PinnedFieldsManager;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewPart;

/**
 * Tests pinning fields to the top of the Variables and Expressions views through their context menu.
 */
public class PinnedFieldsViewTests extends AbstractDebugUiTests {

	private static final String TYPE_NAME = "InstanceVariablesTests";
	protected static final String PIN_TO_TOP = "Pin to Top";
	protected static final String MOVE_PIN_UP = "Move Pin Up";
	protected static final String MOVE_PIN_DOWN = "Move Pin Down";
	protected static final String UNPIN_ALL = "Unpin All Fields";
	private static final long TIMEOUT = 30_000;

	/**
	 * Words identifying the errors caused by the "Pin to Top" contributions
	 */
	private static final List<String> PIN_ERROR_MARKERS = List.of("isPinned", "selectionIsPinnedField", "selectionIsJavaFields",
			"PinnedField", "PinField", "MovePin", "UnpinAll");

	private IJavaThread fThread;
	/**
	 * The suspended frame and its <code>this</code>, captured on suspend: the top frame of the thread is not available
	 * while the views evaluate the details of the values
	 */
	private IJavaStackFrame fFrame;
	private IJavaObject fThis;
	private String fViewId;
	private final List<IStatus> fLoggedErrors = new CopyOnWriteArrayList<>();
	private final ILogListener fLogListener = (status, plugin) -> {
		if (status.matches(IStatus.ERROR)) {
			fLoggedErrors.add(status);
		}
	};

	public PinnedFieldsViewTests(String name) {
		super(name);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Platform.addLogListener(fLogListener);
		PinnedFieldsManager.getDefault().unpinAll();
		resetDebugPerspective();
		processUiEvents(100);
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			PinnedFieldsManager.getDefault().unpinAll();
			terminateAndRemove(fThread);
			removeAllBreakpoints();
			processUiEvents(100);
			Platform.removeLogListener(fLogListener);
			assertNoPinError();
		} finally {
			super.tearDown();
		}
	}

	/**
	 * Fails if an error caused by the "Pin to Top" contributions was logged during the test.
	 */
	protected void assertNoPinError() {
		for (IStatus status : fLoggedErrors) {
			StringWriter text = new StringWriter();
			text.append(status.getMessage());
			if (status.getException() != null) {
				status.getException().printStackTrace(new PrintWriter(text));
			}
			for (String marker : PIN_ERROR_MARKERS) {
				if (text.toString().contains(marker)) {
					fail("Error logged: " + text);
				}
			}
		}
	}

	@Override
	protected boolean enableUIEventLoopProcessingInWaiter() {
		return true;
	}

	public void testPinToTop() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		List<String> initial = waitForChildren(viewer);
		assertTrue("privStr should not be the first field: " + initial, initial.indexOf("privStr") > 0);
		Image unpinnedImage = getChildImage(viewer, "privStr");

		selectChild(viewer, "privStr");
		withContextMenu(viewer, menu -> {
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			assertTrue("'Pin to Top' should be enabled", pin.isEnabled());
			assertFalse("'Pin to Top' should not be checked", pin.getSelection());
			assertNull("'Move Pin Up' should be hidden for an unpinned field", findItem(menu, MOVE_PIN_UP));
			click(pin);
		});

		waitForFirstChildren(viewer, "privStr");
		assertNotSame("Pinned field should be decorated", unpinnedImage, getChildImage(viewer, "privStr"));

		selectChild(viewer, "privStr");
		withContextMenu(viewer, menu -> {
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			assertTrue("'Pin to Top' should be checked for a pinned field", pin.getSelection());
			MenuItem moveUp = findItem(menu, MOVE_PIN_UP);
			assertNotNull("'Move Pin Up' should be shown for a pinned field", moveUp);
			assertFalse("'Move Pin Up' should be disabled for the only pinned field", moveUp.isEnabled());
			click(pin);
		});

		waitFor("Unpinned field should be back to its place: " + initial, () -> initial.equals(getChildNames(viewer)), () -> getChildNames(viewer));
		assertFalse(PinnedFieldsManager.getDefault().hasPinnedFields());
	}

	public void testMovePin() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		waitForChildren(viewer);

		pinFromContextMenu(viewer, "privStr");
		pinFromContextMenu(viewer, "date");
		waitForFirstChildren(viewer, "privStr", "date");

		selectChild(viewer, "date");
		withContextMenu(viewer, menu -> {
			assertFalse("'Move Pin Down' should be disabled for the last pinned field", findItem(menu, MOVE_PIN_DOWN).isEnabled());
			MenuItem moveUp = findItem(menu, MOVE_PIN_UP);
			assertTrue("'Move Pin Up' should be enabled for the last pinned field", moveUp.isEnabled());
			click(moveUp);
		});
		waitForFirstChildren(viewer, "date", "privStr");

		selectChild(viewer, "date");
		withContextMenu(viewer, menu -> click(findItem(menu, MOVE_PIN_DOWN)));
		waitForFirstChildren(viewer, "privStr", "date");
	}

	public void testUnpinAll() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		List<String> initial = waitForChildren(viewer);
		pinFromContextMenu(viewer, "date");
		waitForFirstChildren(viewer, "date");

		selectChild(viewer, "pubStr");
		withContextMenu(viewer, menu -> {
			MenuItem unpinAll = findItem(menu, UNPIN_ALL);
			assertNotNull("Missing 'Unpin All Fields' in " + itemTexts(menu), unpinAll);
			assertTrue("'Unpin All Fields' should be enabled", unpinAll.isEnabled());
			click(unpinAll);
		});

		waitFor("Fields should be back to their place: " + initial, () -> initial.equals(getChildNames(viewer)), () -> getChildNames(viewer));
		selectChild(viewer, "pubStr");
		withContextMenu(viewer, menu -> assertFalse("'Unpin All Fields' should be disabled", findItem(menu, UNPIN_ALL).isEnabled()));
	}

	public void testMultipleSelection() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		waitForChildren(viewer);
		pinFromContextMenu(viewer, "date");
		waitForFirstChildren(viewer, "date");

		// mixed selection: the command pins all the selected fields
		selectChildren(viewer, "date", "privStr");
		withContextMenu(viewer, menu -> {
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			assertFalse("'Pin to Top' should not be checked for a mixed selection", pin.getSelection());
			assertNull("'Move Pin Up' should be hidden for a multiple selection", findItem(menu, MOVE_PIN_UP));
			click(pin);
		});
		waitForFirstChildren(viewer, "date", "privStr");

		// all selected fields pinned: the command unpins them
		selectChildren(viewer, "date", "privStr");
		withContextMenu(viewer, menu -> {
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertTrue("'Pin to Top' should be checked when all the selected fields are pinned", pin.getSelection());
			click(pin);
		});
		waitFor("Fields should be unpinned", () -> !PinnedFieldsManager.getDefault().hasPinnedFields());
	}

	public void testLocalVariableCannotBePinned() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		waitFor("Missing local variable 'ivt'", () -> findRootItem(viewer, "ivt") != null);
		sync(() -> {
			viewer.setSelection(new TreeSelection(new TreePath(new Object[] { findRootItem(viewer, "ivt").getData() })), true);
			return null;
		});
		waitFor("Local variable not selected", () -> viewer.getStructuredSelection().getFirstElement() instanceof IVariable variable
				&& variable.getName().equals("ivt"));
		withContextMenu(viewer, menu -> {
			assertNull("'Pin to Top' should be hidden for a local variable", findItem(menu, PIN_TO_TOP));
			assertNull("'Unpin All Fields' should be hidden for a local variable", findItem(menu, UNPIN_ALL));
		});
	}

	public void testNoErrorForOtherSelections() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		waitForChildren(viewer);
		IViewPart debugView = openView(IDebugUIConstants.ID_DEBUG_VIEW);
		sync(() -> {
			getActivePage().activate(debugView);
			TreePath path = new TreePath(new Object[] { fThread.getLaunch(), fThread.getDebugTarget(), fThread, fFrame });
			debugView.getAdapter(IDebugView.class).getViewer().setSelection(new TreeSelection(path), true);
			return null;
		});
		processUiEvents(500);
		// the handlers of the "Pin to Top" commands are evaluated against the stack frame selection: nothing must be logged
		assertNoPinError();
	}

	public void testPinInExpressionsView() throws Exception {
		IExpressionManager expressionManager = DebugPlugin.getDefault().getExpressionManager();
		IWatchExpression expression = expressionManager.newWatchExpression("this");
		expressionManager.addExpression(expression);
		try {
			TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_EXPRESSION_VIEW);
			List<String> initial = waitForChildren(viewer);
			assertTrue("privStr should not be the first field: " + initial, initial.indexOf("privStr") > 0);

			pinFromContextMenu(viewer, "privStr");

			waitForFirstChildren(viewer, "privStr");
		} finally {
			expressionManager.removeExpression(expression);
		}
	}

	/**
	 * Launches the test program, suspends it where <code>this</code> is an <code>InstanceVariablesTests</code> and shows
	 * the given view.
	 */
	protected TreeViewer launchAndShowThis(String viewId) throws Exception {
		fViewId = viewId;
		ILineBreakpoint bp = createLineBreakpoint(33, TYPE_NAME);
		fThread = launchToLineBreakpoint(TYPE_NAME, bp);
		assertNotNull("Launch unsuccessful", fThread);
		fFrame = (IJavaStackFrame) fThread.getTopStackFrame();
		assertNotNull("Missing top frame", fFrame);
		fThis = fFrame.getThis();
		assertNotNull("'this' is null", fThis);

		IViewPart part = openView(viewId);
		TreeViewer viewer = (TreeViewer) part.getAdapter(IDebugView.class).getViewer();
		sync(() -> getActivePage().activate(part));
		waitFor("'this' not shown in " + viewId, () -> findRootItem(viewer) != null);
		return viewer;
	}

	protected void pinFromContextMenu(TreeViewer viewer, String field) throws Exception {
		selectChild(viewer, field);
		withContextMenu(viewer, menu -> {
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			click(pin);
		});
	}

	protected void selectChildren(TreeViewer viewer, String... fields) throws Exception {
		for (String field : fields) {
			waitFor("Missing field " + field, () -> findChildItem(viewer, field) != null);
		}
		sync(() -> {
			List<TreePath> paths = new ArrayList<>();
			for (String field : fields) {
				paths.add(new TreePath(new Object[] { findRootItem(viewer).getData(), findChildItem(viewer, field).getData() }));
			}
			viewer.setSelection(new TreeSelection(paths.toArray(new TreePath[paths.size()])), true);
			return null;
		});
		waitFor("Fields " + List.of(fields) + " not selected", () -> viewer.getStructuredSelection().size() == fields.length);
	}

	protected void selectChild(TreeViewer viewer, String field) throws Exception {
		waitFor("Missing field " + field, () -> findChildItem(viewer, field) != null);
		sync(() -> {
			TreePath path = new TreePath(new Object[] { findRootItem(viewer).getData(), findChildItem(viewer, field).getData() });
			viewer.setSelection(new TreeSelection(path), true);
			return null;
		});
		waitFor("Field " + field + " not selected", () -> {
			Object selected = viewer.getStructuredSelection().getFirstElement();
			return selected instanceof IVariable variable && variable.getName().equals(field);
		});
	}

	protected interface MenuCallback {
		void run(Menu menu) throws Exception;
	}

	/**
	 * Shows the context menu of the viewer, as a right click does, and runs the callback on its items.
	 */
	protected void withContextMenu(TreeViewer viewer, MenuCallback callback) throws Exception {
		sync(() -> {
			Menu menu = viewer.getControl().getMenu();
			assertNotNull("No context menu", menu);
			menu.notifyListeners(SWT.Show, new Event());
			try {
				callback.run(menu);
			} finally {
				menu.notifyListeners(SWT.Hide, new Event());
			}
			return null;
		});
		processUiEvents(100);
	}

	protected static void click(MenuItem item) {
		assertNotNull("Missing menu item", item);
		if ((item.getStyle() & SWT.CHECK) != 0) {
			item.setSelection(!item.getSelection());
		}
		item.notifyListeners(SWT.Selection, new Event());
	}

	protected static MenuItem findItem(Menu menu, String text) {
		for (MenuItem item : menu.getItems()) {
			if (text.equals(cleanText(item))) {
				return item;
			}
		}
		return null;
	}

	protected static List<String> itemTexts(Menu menu) {
		List<String> texts = new ArrayList<>();
		for (MenuItem item : menu.getItems()) {
			texts.add(cleanText(item));
		}
		return texts;
	}

	protected static String cleanText(MenuItem item) {
		String text = item.getText().replace("&", "");
		int tab = text.indexOf('\t');
		return tab < 0 ? text : text.substring(0, tab);
	}

	/**
	 * Waits until the view shows all the fields of <code>this</code>, in the order computed from the model while no
	 * field is pinned, and returns that order.
	 */
	protected List<String> waitForChildren(TreeViewer viewer) throws Exception {
		assertFalse("No field should be pinned yet", PinnedFieldsManager.getDefault().hasPinnedFields());
		List<String> expected = new ArrayList<>();
		for (Object field : JavaContentProviderFilter.filterVariables(fThis.getVariables(), new PresentationContext(fViewId))) {
			expected.add(((IVariable) field).getName());
		}
		assertTrue("Unexpected fields: " + expected, expected.indexOf("privStr") > 0 && expected.contains("date"));
		waitFor("Fields of 'this' not all shown, expected " + expected, () -> {
			// the view may be refreshed while it is being populated, so expand until the fields are shown
			TreeItem root = findRootItem(viewer);
			if (root != null && !root.getExpanded()) {
				viewer.expandToLevel(new TreePath(new Object[] { root.getData() }), 1);
			}
			return expected.equals(getChildNames(viewer));
		}, () -> getChildNames(viewer));
		return expected;
	}

	protected void waitForFirstChildren(TreeViewer viewer, String... fields) throws Exception {
		List<String> expected = List.of(fields);
		waitFor("Expected first fields " + expected, () -> {
			List<String> names = getChildNames(viewer);
			return names.size() >= expected.size() && names.subList(0, expected.size()).equals(expected);
		}, () -> getChildNames(viewer));
	}

	protected Image getChildImage(TreeViewer viewer, String field) throws Exception {
		return sync(() -> findChildItem(viewer, field).getImage());
	}

	protected void waitFor(String message, Callable<Boolean> condition) throws Exception {
		waitFor(message, condition, () -> null);
	}

	/**
	 * Waits for the condition, and fails with the given message and the state described by the given callable.
	 */
	protected void waitFor(String message, Callable<Boolean> condition, Callable<Object> state) throws Exception {
		long end = System.currentTimeMillis() + TIMEOUT;
		while (System.currentTimeMillis() < end) {
			if (Boolean.TRUE.equals(sync(condition))) {
				return;
			}
			processUiEvents(100);
		}
		Object shown = sync(state);
		fail(shown == null ? message : message + ", shown: " + shown);
	}

	protected static TreeItem findRootItem(TreeViewer viewer) throws DebugException {
		return findRootItem(viewer, "this");
	}

	protected static TreeItem findRootItem(TreeViewer viewer, String name) throws DebugException {
		for (TreeItem item : viewer.getTree().getItems()) {
			if (item.getData() instanceof IVariable variable && variable.getName().equals(name)) {
				return item;
			}
			if (item.getData() instanceof IWatchExpression expression && expression.getExpressionText().equals(name)) {
				return item;
			}
		}
		return null;
	}

	protected static TreeItem findChildItem(TreeViewer viewer, String field) throws DebugException {
		TreeItem root = findRootItem(viewer);
		if (root != null) {
			for (TreeItem item : root.getItems()) {
				if (item.getData() instanceof IVariable variable && variable.getName().equals(field)) {
					return item;
				}
			}
		}
		return null;
	}

	protected static List<String> getChildNames(TreeViewer viewer) throws DebugException {
		List<String> names = new ArrayList<>();
		TreeItem root = findRootItem(viewer);
		if (root != null) {
			for (TreeItem item : root.getItems()) {
				if (item.getData() instanceof IVariable variable) {
					names.add(variable.getName());
				}
			}
		}
		return names;
	}
}
