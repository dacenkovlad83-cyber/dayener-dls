package dls.launcher;

import dls.launcher.ui.LauncherWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * dayener dls launcher: downloads Minecraft {@value GameInstaller#MC} with Fabric, Fabric API and the
 * dayener dls mod, and starts it with an offline nick.
 */
public final class Main {
	private Main() {
	}

	public static void main(String[] args) {
		System.setProperty("awt.useSystemAAFontSettings", "lcd");

		try {
			// Native look for the file choosers; everything else is painted by the launcher itself.
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// default look
		}

		LauncherConfig config = LauncherConfig.load();
		SwingUtilities.invokeLater(() -> new LauncherWindow(config).setVisible(true));
	}
}
