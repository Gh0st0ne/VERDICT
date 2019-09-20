package com.ge.research.osate.verdict.gui;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

public class BundlePreferences extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	private static final String BUNDLE_JAR = "verdict_bundle_jar";

	public BundlePreferences() {
		super();
	}

	private static ScopedPreferenceStore preferenceStore = null;

	public static ScopedPreferenceStore getVerdictPreferenceStore() {
		if (preferenceStore == null) {
			preferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, "com.ge.research.osate.verdict");
		}
		return preferenceStore;
	}

	public static String getBundleJar() {
		return getVerdictPreferenceStore().getString(BUNDLE_JAR);
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(getPreferenceStore());
		setDescription("Preferences for Verdict bundle (MBAS/CRV)");
		this.noDefaultAndApplyButton();
	}

	@Override
	protected void createFieldEditors() {
		FileFieldEditor bundleJar = new FileFieldEditor(BUNDLE_JAR, "Bundle JAR:", true,
				StringFieldEditor.VALIDATE_ON_KEY_STROKE, getFieldEditorParent());
		bundleJar.setFileExtensions(new String[] { "*.jar" });
		bundleJar.setStringValue(getBundleJar());
		addField(bundleJar);

		// The preferences won't save any other way
		// This is really ugly, and it makes it save even if the user doesn't press "Apply and Close"
		// But at least it saves the preferences instead of not saving them at all

		Text text = null;

		try {
			Field field = StringFieldEditor.class.getDeclaredField("textField");
			field.setAccessible(true);

			text = (Text) field.get(bundleJar);
		} catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
			e.printStackTrace();
		}

		if (text != null) {
			text.addModifyListener(event -> {
				String path = bundleJar.getStringValue();
				File file = new File(path);
				if (file.exists() && file.isFile() && path.endsWith(".jar")) {
					ScopedPreferenceStore prefs = getVerdictPreferenceStore();
					prefs.putValue(BUNDLE_JAR, path);
					try {
						prefs.save();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}
}