package com.mobhunt;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.*;
import net.minecraft.text.Text;

public class MobHuntModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            MobHuntConfig cfg = MobHuntConfig.get();
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("MobHunt Settings"))
                    .setSavingRunnable(MobHuntConfig::save);

            ConfigEntryBuilder eb = builder.entryBuilder();
            ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

            // Default timer
            general.addEntry(eb.startIntSlider(
                            Text.literal("Default Timer (minutes)"),
                            cfg.defaultTimerMinutes, 1, 120)
                    .setDefaultValue(10)
                    .setTooltip(Text.literal("Timer used when /start is run with no argument"))
                    .setSaveConsumer(v -> cfg.defaultTimerMinutes = v)
                    .build()
            );

            // Kill mode
            general.addEntry(eb.startEnumSelector(
                            Text.literal("Kill Mode"),
                            MobHuntConfig.KillMode.class,
                            cfg.killMode)
                    .setDefaultValue(MobHuntConfig.KillMode.ALL)
                    .setTooltip(
                            Text.literal("ALL — any mob resets the timer"),
                            Text.literal("HOSTILE — only hostile mobs count"),
                            Text.literal("PASSIVE — only passive mobs count")
                    )
                    .setSaveConsumer(v -> cfg.killMode = v)
                    .build()
            );

            return builder.build();
        };
    }
}