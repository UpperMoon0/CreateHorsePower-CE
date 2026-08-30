package net.steampn.createhorsepower.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class CHPCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("createhorsepower")
                .then(Commands.literal("inspect")
                        .executes(CHPCommands::inspectTargetCrank))
                .then(Commands.literal("worker")
                        .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .executes(CHPCommands::inspectWorkerType)))
                .then(Commands.literal("path")
                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(CHPCommands::inspectPathBlock)))
        );
    }

    private static int inspectTargetCrank(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return CHPCommandHandlers.inspectTargetCrank(context.getSource());
    }

    private static int inspectWorkerType(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Holder.Reference<EntityType<?>> holder = ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE);
        EntityType<?> type = holder.value();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return CHPCommandHandlers.inspectWorker(context.getSource(), type, id);
    }

    private static int inspectPathBlock(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Holder.Reference<Block> holder = ResourceArgument.getResource(context, "block", Registries.BLOCK);
        Block block = holder.value();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return CHPCommandHandlers.inspectPath(context.getSource(), block, id);
    }
}
