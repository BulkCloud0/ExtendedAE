package com.glodblock.github.extendedae.recipe.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("deprecation")
public class FluidIngredient implements Predicate<FluidStack> {

    private final List<Fluid> fluids = new ArrayList<Fluid>();
    protected final Value value;

    public List<Fluid> getFluid() {
        return this.fluids;
    }

    public static FluidIngredient of(FriendlyByteBuf buff) {
        byte type = buff.readByte();
        if (type == 0) {
            return new FluidIngredient(new FluidValue(buff.readFluidStack()));
        } else if (type == 1) {
            return new FluidIngredient(new TagValue(TagKey.create(Registries.FLUID, buff.readResourceLocation())));
        }
        throw new IllegalArgumentException("Unknown fluid ingredient type: " + type);
    }

    public static FluidIngredient of(JsonElement json) {
        if (json != null && json.isJsonObject()) {
            JsonObject obj = (JsonObject) json;
            if (obj.has("fluid")) {
                Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(obj.get("fluid").getAsString()));
                return new FluidIngredient(new FluidValue(new FluidStack(fluid, 1000)));
            } else if (obj.has("tag")) {
                ResourceLocation tag = new ResourceLocation(obj.get("tag").getAsString());
                return new FluidIngredient(new TagValue(TagKey.create(Registries.FLUID, tag)));
            }
        }
        return new FluidIngredient(new FluidValue(FluidStack.EMPTY));
    }

    public static FluidIngredient of(FluidStack fluid) {
        return new FluidIngredient(new FluidValue(fluid));
    }

    public static FluidIngredient of(TagKey<Fluid> fluid) {
        return new FluidIngredient(new TagValue(fluid));
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        if (this.value instanceof FluidValue) {
            FluidValue value = (FluidValue) this.value;
            json.addProperty("fluid", BuiltInRegistries.FLUID.getKey(value.fluid().getFluid()).toString());
        } else if (this.value instanceof TagValue) {
            TagValue value = (TagValue) this.value;
            json.addProperty("tag", value.fluid().location().toString());
        }
        return json;
    }

    public void to(FriendlyByteBuf buff) {
        if (this.value instanceof FluidValue) {
            FluidValue value = (FluidValue) this.value;
            buff.writeByte(0);
            buff.writeFluidStack(value.fluid());
        } else if (this.value instanceof TagValue) {
            TagValue value = (TagValue) this.value;
            buff.writeByte(1);
            buff.writeResourceLocation(value.fluid().location());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public FluidIngredient(Value v) {
        this.value = v;
        if (v instanceof FluidValue) {
            FluidStack fluid = ((FluidValue) v).fluid();
            if (!fluid.isEmpty()) {
                this.fluids.add(fluid.getFluid());
            }
        } else if (v instanceof TagValue) {
            TagValue tag = (TagValue) v;
            for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag.fluid())) {
                this.fluids.add(holder.value());
            }
        }
    }

    @Override
    public boolean test(FluidStack fluidStack) {
        if (this.fluids.isEmpty()) {
            return fluidStack.isEmpty();
        }
        if (fluidStack.isEmpty()) {
            return false;
        }
        Fluid fluid = fluidStack.getFluid();
        for (Fluid taggedFluid : this.fluids) {
            if (taggedFluid.isSame(fluid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        if (this.value instanceof TagValue) {
            return "tag: " + ((TagValue) this.value).fluid();
        }
        if (this.value instanceof FluidValue) {
            return "fluid: " + ((FluidValue) this.value).fluid();
        }
        return super.toString();
    }

    public interface Value {}

    public static final class TagValue implements Value {
        private final TagKey<Fluid> fluid;

        public TagValue(TagKey<Fluid> fluid) {
            this.fluid = fluid;
        }

        public TagKey<Fluid> fluid() {
            return this.fluid;
        }
    }

    public static final class FluidValue implements Value {
        private final FluidStack fluid;

        public FluidValue(FluidStack fluid) {
            this.fluid = fluid;
        }

        public FluidStack fluid() {
            return this.fluid;
        }
    }
}
