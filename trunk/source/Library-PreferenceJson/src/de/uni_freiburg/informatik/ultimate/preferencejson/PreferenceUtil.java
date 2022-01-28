/*
 * Copyright (C) 2022 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2022 University of Freiburg
 * 
 * This file is part of the ULTIMATE Core.
 * 
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.preferencejson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.core.model.ICore;
import de.uni_freiburg.informatik.ultimate.core.model.IUltimatePlugin;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.BaseUltimatePreferenceItem;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.UltimatePreferenceItem;

/**
 * 
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public class PreferenceUtil {

	/**
	 * This method converts the label of an {@link UltimatePreferenceItem} to a string that can be used on the command
	 * line or in the web interface.
	 */
	public static String convertLabelToLongName(final String pluginId, final UltimatePreferenceItem<?> item) {
		final String label = item.getLabel();
		final int lastIdx = pluginId.lastIndexOf('.');
		final String prefix = lastIdx > 0 ? pluginId.substring(lastIdx + 1) : pluginId;
		final String unprocessedName = prefix + " " + label;
		return unprocessedName.replace(":", "").replaceAll("[ ()\"'.]+", ".").toLowerCase(Locale.ENGLISH);
	}

	public static String generateJson() {
		/*
		 * { // name: Settings name displayed in the settings menu. name: "Check for memory leak in main procedure", //
		 * id: A mandatory unique id for this setting. id: "chck_main_mem_leak", // plugin_id: Ultimate plugin affected
		 * by this setting. plugin_id: "de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator",
		 * // key: Setting key as used by the plugin. key:
		 * "Check for the main procedure if all allocated memory was freed", // type: Setting type can be one of
		 * ("bool", "int", "string", "real") type: "bool", // default: Default state/value for the setting. default:
		 * true, // range: If the type is "int" or "real", a slider will be generated in the frontend. // range: [1,
		 * 12], // options: If the type is "string", a selection field will be generated in the frontend. // options:
		 * ["foo", "bar", "baz"] // visible: If true, this setting is exposed to the user. visible: true }
		 * 
		 */

		final Map<String, Object> frontendSettings = Map.of("name", null, "id", null, "plugin_id", null, "key", null,
				"type", null, "default", null, "visible", false);

		return "";
	}

	private List<Map<String, Object>> createUltimateOptions(final ICore<?> core) {
		final List<Map<String, Object>> rtr = new ArrayList<>();
		for (final IUltimatePlugin plugin : core.getRegisteredUltimatePlugins()) {
			final IPreferenceInitializer preferences = plugin.getPreferences();
			if (preferences == null) {
				continue;
			}
			// TODO: Add IPreferenceProvider
			final String pluginId = preferences.getPluginID();
			for (final UltimatePreferenceItem<?> item : BaseUltimatePreferenceItem
					.constructFlattenedList(preferences.getPreferenceItems())) {
				final Map<String, Object> setting = createFrontendSetting(item, pluginId);
				if (setting == null) {
					// skip labels and containers
					continue;
				}
				rtr.add(setting);
			}
		}
		return rtr;
	}

	private Map<String, Object> createFrontendSetting(final UltimatePreferenceItem<?> item, final String pluginId) {
		// TODO Auto-generated method stub
		return null;
	}

	// private Option createOption(final UltimatePreferenceItem<?> item, final String pluginId) {
	// switch (item.getType()) {
	// case Label:
	// case SubItemContainer:
	// return null;
	// default:
	// break;
	// }
	//
	// final Builder builder = createBuilder(item, pluginId);
	//
	// switch (item.getType()) {
	// case Boolean:
	// return builder.hasArg(true).numberOfArgs(1).type(Boolean.class).build();
	// case Integer:
	// return builder.hasArg(true).numberOfArgs(1).type(Integer.class).build();
	// case Double:
	// return builder.hasArg(true).numberOfArgs(1).type(Double.class).build();
	// case KeyValue:
	// // return builder.hasArg().numberOfArgs(1).type(String.class).build();
	// case Combo:
	// case Color:
	// case Directory:
	// case File:
	// case MultilineString:
	// case Path:
	// case Radio:
	// case String:
	// return builder.hasArg(true).numberOfArgs(1).type(String.class).build();
	// default:
	// throw new IllegalArgumentException("PreferenceItem type " + item.getType() + " is not supported yet");
	// }
	// }

}
