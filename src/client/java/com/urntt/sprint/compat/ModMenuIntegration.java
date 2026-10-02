package com.urntt.sprint.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.urntt.sprint.config.SprintConfigScreen;

/**
 * Mod Menu entrypoint. This is the only class that may reference Mod Menu, which is an optional dependency.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SprintConfigScreen::new;
	}
}
