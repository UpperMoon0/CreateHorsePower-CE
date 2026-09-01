package net.steampn.createhorsepower.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class CHPCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("createhorsepower")
                .then(Commands.literal("inspect")
                        .executes(CHPCommands::inspectTargetCrank))
                .then(Commands.literal("worker")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .executes(CHPCommands::inspectWorkerType)))
                .then(Commands.literal("path")
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .executes(CHPCommands::inspectPathBlock)))
        );
    }

    private static int inspectTargetCrank(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return CHPCommandHandlers.inspectTargetCrank(context.getSource());
    }

    private static int inspectWorkerType(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "entity");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return CHPCommandHandlers.inspectWorker(context.getSource(), type, id);
    }

    private static int inspectPathBlock(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "block");
        Block block = BuiltInRegistries.BLOCK.get(id);
        return CHPCommandHandlers.inspectPath(context.getSource(), block, id);
    }
}
