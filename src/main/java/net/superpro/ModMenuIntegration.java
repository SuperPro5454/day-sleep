package net.superpro;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.impl.controller.IntegerFieldControllerBuilderImpl;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    public static Integer gameSpeedMultiplier = 5;

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parentScreen -> YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Day Sleep Config"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Day Sleep Config"))
                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Game Speed Multiplier"))
                                .description(OptionDescription.of(Text.literal("The multiplier for game time (how many times the server will run the tick function during a single tick).")))
                                .binding(5, () -> gameSpeedMultiplier, newVal -> gameSpeedMultiplier = newVal)
                                .controller(option -> new IntegerFieldControllerBuilderImpl(option).formatValue(value -> Text.literal(value + "x")).range(3, 50))
                                .build())
                        .build())
                .build()
                .generateScreen(parentScreen);
    }
}