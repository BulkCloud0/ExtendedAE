package com.glodblock.github.extendedae.xmod.jei.transfer;

import appeng.api.stacks.AEItemKey;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.FillCraftingGridFromRecipePacket;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.CraftingRecipeUtil;
import com.glodblock.github.extendedae.container.ContainerExCraftingTerminal;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ExCraftingHelper {

    private static final Comparator<GridInventoryEntry> ENTRY_COMPARATOR = Comparator.comparing(GridInventoryEntry::getStoredAmount);

    public static void performTransfer(ContainerExCraftingTerminal menu, Recipe<?> recipe, int recipeSize, boolean craftMissing) {
        NonNullList<ItemStack> templateItems = findGoodTemplateItems(recipe, recipeSize, menu);
        ResourceLocation recipeId = recipe.getId();
        if (menu.getPlayer().level().getRecipeManager().byKey(recipe.getId()).isEmpty()) {
            AELog.debug("Cannot send recipe id %s to server because it's transient", recipeId);
            recipeId = null;
        }
        NetworkHandler.instance().sendToServer(new FillCraftingGridFromRecipePacket(recipeId, templateItems, craftMissing));
    }

    private static NonNullList<ItemStack> findGoodTemplateItems(Recipe<?> recipe, int recipeSize, MEStorageMenu menu) {
        NonNullList<ItemStack> templateItems = NonNullList.withSize(recipeSize, ItemStack.EMPTY);
        List<Ingredient> ingredients = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        for (int i = 0; i < Math.min(ingredients.size(), recipeSize); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (!ingredient.isEmpty()) {
                ItemStack stack = EncodingHelper.getIngredientPriorities(menu, ENTRY_COMPARATOR)
                        .entrySet()
                        .stream()
                        .filter(e -> e.getKey() instanceof AEItemKey && ((AEItemKey) e.getKey()).matches(ingredient))
                        .max(Comparator.comparingInt(Map.Entry::getValue))
                        .map(e -> ((AEItemKey) e.getKey()).toStack())
                        .orElse(ingredient.getItems()[0]);
                templateItems.set(i, stack);
            }
        }
        return templateItems;
    }
}
