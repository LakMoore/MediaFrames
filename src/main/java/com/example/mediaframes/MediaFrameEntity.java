package com.example.mediaframes;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.PaintingEntity;
import net.minecraft.world.World;

public class MediaFrameEntity extends PaintingEntity {

    public MediaFrameEntity(EntityType<? extends PaintingEntity> entityType, World world) {
        super(entityType, world);
    }
    
}
