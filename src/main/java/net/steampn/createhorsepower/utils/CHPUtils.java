package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class CHPUtils {
    public static InteractionResult killLeashEntity(Level level, BlockPos pos){
        level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(pos).inflate(0.2D))
                .forEach(Entity::kill);
        return InteractionResult.SUCCESS;
    }
}
