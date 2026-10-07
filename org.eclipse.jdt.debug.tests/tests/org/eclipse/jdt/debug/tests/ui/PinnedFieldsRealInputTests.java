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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * CI-only verification: drives the Variables view with real mouse and keyboard events injected in the X server, and
 * takes screenshots of the whole screen in the directory given by the <code>PIN_SCREENSHOTS</code> environment
 * variable.
 */
public class PinnedFieldsRealInputTests extends PinnedFieldsViewTests {

	public PinnedFieldsRealInputTests(String name) {
		super(name);
	}

	public void testRealUserInteraction() throws Exception {
		TreeViewer viewer = launchAndShowThis(IDebugUIConstants.ID_VARIABLE_VIEW);
		List<String> initial = waitForChildren(viewer);
		sync(() -> {
			viewer.getControl().getShell().forceActive();
			return null;
		});
		processUiEvents(1000);
		screenshot("01-variables-view-initial");

		openContextMenuWithMouse(viewer, "privStr");
		screenshot("02-context-menu-on-unpinned-field");
		sync(() -> {
			Menu menu = viewer.getTree().getMenu();
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			assertFalse(pin.getSelection());
			assertNull(findItem(menu, MOVE_PIN_UP));
			return null;
		});
		activateWithKeyboard(viewer, PIN_TO_TOP);
		waitForFirstChildren(viewer, "privStr");
		processUiEvents(1000);
		screenshot("03-privStr-pinned");

		openContextMenuWithMouse(viewer, "privStr");
		screenshot("04-context-menu-on-pinned-field");
		sync(() -> {
			Menu menu = viewer.getTree().getMenu();
			MenuItem pin = findItem(menu, PIN_TO_TOP);
			assertNotNull("Missing 'Pin to Top' in " + itemTexts(menu), pin);
			assertTrue("'Pin to Top' should be checked on a pinned field", pin.getSelection());
			assertNotNull("Missing 'Move Pin Up' in " + itemTexts(menu), findItem(menu, MOVE_PIN_UP));
			assertNotNull("Missing 'Move Pin Down' in " + itemTexts(menu), findItem(menu, MOVE_PIN_DOWN));
			assertUniqueMnemonics(menu, PIN_TO_TOP, MOVE_PIN_UP, MOVE_PIN_DOWN, UNPIN_ALL);
			return null;
		});
		pressKey("Escape");

		openContextMenuWithMouse(viewer, "date");
		activateWithKeyboard(viewer, PIN_TO_TOP);
		waitForFirstChildren(viewer, "privStr", "date");
		processUiEvents(1000);
		screenshot("05-privStr-and-date-pinned");

		openContextMenuWithMouse(viewer, "date");
		activateWithKeyboard(viewer, MOVE_PIN_UP);
		waitForFirstChildren(viewer, "date", "privStr");
		processUiEvents(1000);
		screenshot("06-date-moved-up");

		openContextMenuWithMouse(viewer, "pubStr");
		activateWithKeyboard(viewer, UNPIN_ALL);
		waitFor("Fields should be back to their place", () -> initial.equals(getChildNames(viewer)));
		processUiEvents(1000);
		screenshot("07-all-unpinned");
	}

	private void openContextMenuWithMouse(TreeViewer viewer, String field) throws Exception {
		waitFor("Missing field " + field, () -> findChildItem(viewer, field) != null);
		Point location = sync(() -> {
			Tree tree = viewer.getTree();
			TreeItem item = findChildItem(viewer, field);
			tree.showItem(item);
			Rectangle bounds = item.getBounds();
			return tree.toDisplay(bounds.x + Math.min(bounds.width / 2, 40), bounds.y + bounds.height / 2);
		});
		int[] shown = new int[1];
		Listener listener = event -> shown[0]++;
		sync(() -> {
			viewer.getTree().getMenu().addListener(SWT.Show, listener);
			return null;
		});
		try {
			xdotool("mousemove", "--sync", String.valueOf(location.x), String.valueOf(location.y), "click", "3");
			processUiEvents(1000);
			screenshot("debug-after-right-click-on-" + field);
			waitFor("Context menu not shown on " + field, () -> shown[0] > 0);
		} finally {
			sync(() -> {
				viewer.getTree().getMenu().removeListener(SWT.Show, listener);
				return null;
			});
		}
		processUiEvents(500);
	}

	/**
	 * Selects the given item of the visible context menu with the arrow keys, as GTK does: separators and disabled
	 * items are skipped.
	 */
	private void activateWithKeyboard(TreeViewer viewer, String text) throws Exception {
		int presses = sync(() -> {
			Menu menu = viewer.getTree().getMenu();
			int count = 0;
			for (MenuItem item : menu.getItems()) {
				if ((item.getStyle() & SWT.SEPARATOR) != 0 || !item.isEnabled()) {
					continue;
				}
				count++;
				if (text.equals(cleanText(item))) {
					return count;
				}
			}
			fail("Missing enabled '" + text + "' in " + itemTexts(menu));
			return 0;
		});
		for (int i = 0; i < presses; i++) {
			pressKey("Down");
		}
		pressKey("Return");
		processUiEvents(500);
	}

	private static void assertUniqueMnemonics(Menu menu, String... texts) {
		java.util.Map<Character, List<String>> byMnemonic = new java.util.HashMap<>();
		for (MenuItem item : menu.getItems()) {
			String text = item.getText();
			int index = text.indexOf('&');
			if (index >= 0 && index + 1 < text.length()) {
				byMnemonic.computeIfAbsent(Character.toLowerCase(text.charAt(index + 1)), k -> new ArrayList<>()).add(cleanText(item));
			}
		}
		System.out.println("Context menu mnemonics: " + byMnemonic);
		for (String text : texts) {
			MenuItem item = findItem(menu, text);
			String label = item.getText();
			int index = label.indexOf('&');
			assertTrue("No mnemonic for " + text, index >= 0);
			List<String> sharing = byMnemonic.get(Character.toLowerCase(label.charAt(index + 1)));
			assertEquals("Mnemonic of '" + text + "' is shared: " + sharing, 1, sharing.size());
		}
	}

	private static void pressKey(String key) throws Exception {
		xdotool("key", key);
		processUiEvents(200);
	}

	/**
	 * Injects real input events in the X server, as a user would.
	 */
	private static void xdotool(String... args) throws Exception {
		List<String> command = new ArrayList<>();
		command.add("xdotool");
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).inheritIO().start();
		// let the UI thread run while xdotool waits for its events to be processed
		while (process.isAlive()) {
			processUiEvents(50);
		}
		assertEquals("Failed: " + command, 0, process.exitValue());
	}

	private static void screenshot(String name) {
		String directory = System.getenv("PIN_SCREENSHOTS");
		if (directory == null) {
			return;
		}
		sync(() -> {
			Display display = Display.getCurrent();
			Rectangle bounds = display.getBounds();
			Image image = new Image(display, bounds.width, bounds.height);
			GC gc = new GC(display);
			try {
				gc.copyArea(image, bounds.x, bounds.y);
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] { image.getImageData() };
				loader.save(directory + "/" + name + ".png", SWT.IMAGE_PNG);
			} finally {
				gc.dispose();
				image.dispose();
			}
			return null;
		});
	}
}
