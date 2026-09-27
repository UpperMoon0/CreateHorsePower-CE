package net.steampn.createhorsepower.datagen;

import com.simibubi.create.AllBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.util.function.Consumer;

public final class CHPRecipeProvider extends RecipeProvider {
    public CHPRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BlockRegister.HORSE_CRANK.get())
                .pattern(CHPDataDefinitions.HORSE_CRANK_RECIPE[0])
                .pattern(CHPDataDefinitions.HORSE_CRANK_RECIPE[1])
                .pattern(CHPDataDefinitions.HORSE_CRANK_RECIPE[2])
                .define('F', Blocks.OAK_FENCE)
                .define('C', AllBlocks.COGWHEEL.get())
                .define('S', Blocks.STONE)
                .unlockedBy(getHasName(AllBlocks.COGWHEEL.get()), has(AllBlocks.COGWHEEL.get()))
                .save(output);
    }
}
