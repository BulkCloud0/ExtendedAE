package com.glodblock.github.extendedae.recipe.util;

import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MachineResult<T extends Recipe<?>> implements FinishedRecipe {

    private final ResourceLocation id;
    private final MachineRecipe<T> machine;
    private final T recipe;

    protected MachineResult(MachineRecipe<T> machine, T recipe, ResourceLocation id) {
        this.id = id;
        this.machine = machine;
        this.recipe = recipe;
    }

    @Override
    public void serializeRecipeData(@NotNull JsonObject jsonObject) {
        this.machine.toJson(jsonObject, this.recipe);
    }

    @Override
    public @NotNull ResourceLocation getId() {
        return this.id;
    }

    @Override
    public @NotNull RecipeSerializer<?> getType() {
        return this.machine;
    }

    @Nullable
    @Override
    public JsonObject serializeAdvancement() {
        return null;
    }

    @Nullable
    @Override
    public ResourceLocation getAdvancementId() {
        return null;
    }

    public static final class Type<T extends Recipe<?>> {
        private final MachineRecipe<T> machine;

        public Type(MachineRecipe<T> machine) {
            this.machine = machine;
        }

        public MachineRecipe<T> machine() {
            return this.machine;
        }

        public static <T extends Recipe<?>> Type<T> type(MachineRecipe<T> machine) {
            return new Type<T>(machine);
        }

        public MachineResult<T> result(ResourceLocation id, T recipe) {
            return new MachineResult<T>(this.machine, recipe, id);
        }
    }
}
