package com.glodblock.github.extendedae.client.gui.widget;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingEvent;
import appeng.helpers.Inventories;
import appeng.helpers.InventoryAction;
import appeng.items.storage.ViewCellItem;
import appeng.menu.slot.AppEngSlot;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CarriedItemInventory;
import appeng.util.inv.PlayerInternalInventory;
import appeng.util.prioritylist.IPartitionList;
import com.glodblock.github.extendedae.container.ContainerExCraftingTerminal;
import lombok.var;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OutputResultSlot extends AppEngSlot {

    private final InternalInventory craftingGrid;
    private final Player player;
    private final RecipeType<? extends Recipe<? extends Container>> type;
    private int amountCrafted;
    private final InternalInventory craftInv;
    private final IActionSource mySrc;
    private final IEnergySource energySrc;
    private final MEStorage storage;
    private final ContainerExCraftingTerminal menu;

    public OutputResultSlot(Player player, IActionSource mySrc, IEnergySource energySrc, MEStorage storage, InternalInventory cMatrix, InternalInventory secondMatrix, ContainerExCraftingTerminal ccp, RecipeType<? extends Recipe<? extends Container>> type) {
        super(new AppEngInternalInventory(1), 0);
        this.player = player;
        this.craftingGrid = cMatrix;
        this.energySrc = energySrc;
        this.storage = storage;
        this.mySrc = mySrc;
        this.craftInv = secondMatrix;
        this.menu = ccp;
        this.type = type;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    protected void onQuickCraft(@NotNull ItemStack stack, int par2) {
        this.amountCrafted += par2;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        stack.onCraftedBy(this.player.level(), this.player, this.amountCrafted);
        this.amountCrafted = 0;
    }

    @Override
    public void onTake(@NotNull Player p, @NotNull ItemStack is) {
        // NO-OP
    }

    public void doClick(InventoryAction action, Player who) {
        if (this.getItem().isEmpty() || this.isRemote()) {
            return;
        }
        final var howManyPerCraft = this.getItem().getCount();

        int maxTimesToCraft;
        InternalInventory target;
        if (action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL) {
            target = new PlayerInternalInventory(who.getInventory());
            if (action == InventoryAction.CRAFT_SHIFT) {
                maxTimesToCraft = (int) Math.floor((double) this.getItem().getMaxStackSize() / (double) howManyPerCraft);
            } else {
                maxTimesToCraft = (int) Math.floor((double) this.getItem().getMaxStackSize() / (double) howManyPerCraft * Inventory.INVENTORY_SIZE);
            }
        } else if (action == InventoryAction.CRAFT_STACK) {
            target = new CarriedItemInventory(getMenu());
            maxTimesToCraft = (int) Math.floor((double) this.getItem().getMaxStackSize() / (double) howManyPerCraft);
        } else {
            if (getMenu().getCarried().isEmpty()) {
                var result = this.craftItem(who, this.storage, this.storage.getAvailableStacks());
                if (!result.isEmpty()) {
                    this.menu.playSound();
                }
                getMenu().setCarried(result);
                return;
            }
            target = new CarriedItemInventory(getMenu());
            maxTimesToCraft = 1;
        }

        var itemAtStart = this.getItem().copy();
        if (itemAtStart.isEmpty()) {
            return;
        }
        boolean playSound = false;
        for (var x = 0; x < maxTimesToCraft; x++) {
            if (!ItemStack.isSameItemSameTags(itemAtStart, getItem())) {
                return;
            }
            if (!target.simulateAdd(itemAtStart).isEmpty()) {
                return;
            }

            var all = this.storage.getAvailableStacks();
            var result = this.craftItem(who, this.storage, all);
            if (!result.isEmpty()) {
                playSound = true;
            }
            var extra = target.addItems(result);
            if (!extra.isEmpty()) {
                Platform.spawnDrops(who.level(), who.blockPosition(), Collections.singletonList(extra));
                if (playSound) {
                    this.menu.playSound();
                }
                return;
            }
        }
        if (playSound) {
            this.menu.playSound();
        }
    }

    protected ItemStack specialHandler(Player p, ItemStack result, CraftingContainer container) {
        var target = result.getItem();
        if (target.canBeDepleted() && target.isValidRepairItem(result, result)) {
            var isBad = false;
            for (var x = 0; x < container.getContainerSize(); x++) {
                final var pis = container.getItem(x);
                if (pis.isEmpty()) {
                    continue;
                }
                if (pis.getItem() != target) {
                    isBad = true;
                }
            }
            if (!isBad) {
                this.runRecipe(p, result);
                p.containerMenu.slotsChanged(this.craftInv.toContainer());
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack craftItem(Player p, MEStorage inv, KeyCounter all) {
        var is = this.getItem().copy();
        if (is.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final var set = new ItemStack[this.craftingGrid.size()];
        Arrays.fill(set, ItemStack.EMPTY);
        var level = p.level();
        if (!level.isClientSide()) {
            final var ic = new TransientCraftingContainer(p.containerMenu, 3, 3);
            for (var x = 0; x < Math.min(9, this.craftingGrid.size()); x++) {
                ic.setItem(x, this.craftingGrid.getStackInSlot(x));
            }
            final Recipe<Container> r = this.findRecipe(ic, level);
            if (r == null) {
                return this.specialHandler(p, is, ic);
            }
            is = r.assemble(ic, level.registryAccess());
            if (inv != null) {
                var filter = ViewCellItem.createItemFilter(this.menu.getViewCells());
                for (var x = 0; x < this.craftingGrid.size(); x++) {
                    if (!this.craftingGrid.getStackInSlot(x).isEmpty()) {
                        set[x] = this.extractItemsByRecipe(inv, level, r, is, ic, this.craftingGrid.getStackInSlot(x), x, all, filter);
                        ic.setItem(x, set[x]);
                    }
                }
            }
        }
        this.runRecipe(p, is);
        this.postCraft(p, inv, set);
        p.containerMenu.slotsChanged(this.craftInv.toContainer());
        return is;
    }

    private void postCraft(Player p, MEStorage inv, ItemStack[] set) {
        final List<ItemStack> drops = new ArrayList<>();
        if (!p.getCommandSenderWorld().isClientSide()) {
            for (var x = 0; x < this.craftInv.size(); x++) {
                if (this.craftInv.getStackInSlot(x).isEmpty()) {
                    this.craftInv.setItemDirect(x, set[x]);
                } else if (!set[x].isEmpty()) {
                    var what = AEItemKey.of(set[x]);
                    var amount = set[x].getCount();
                    var inserted = inv.insert(what, amount, Actionable.MODULATE, this.mySrc);
                    if (what != null && inserted < amount) {
                        drops.add(what.toStack((int) (amount - inserted)));
                    }
                }
            }
        }
        if (!drops.isEmpty()) {
            Platform.spawnDrops(p.level(), new BlockPos((int) p.getX(), (int) p.getY(), (int) p.getZ()), drops);
        }
    }

    public void runRecipe(@NotNull Player playerIn, @NotNull ItemStack stack) {
        CraftingEvent.fireCraftingEvent(playerIn, stack, this.craftingGrid.toContainer());
        this.amountCrafted += stack.getCount();
        this.checkTakeAchievements(stack);
        ForgeHooks.setCraftingPlayer(playerIn);
        final CraftingContainer ic = new TransientCraftingContainer(this.getMenu(), 3, 3);

        for (int x = 0; x < this.craftingGrid.size(); x++) {
            ic.setItem(x, this.craftingGrid.getStackInSlot(x));
        }

        var leftStacks = this.getRemainingItems(ic, playerIn.level());
        Inventories.copy(ic, this.craftingGrid, false);
        ForgeHooks.setCraftingPlayer(null);
        for (int i = 0; i < Math.min(leftStacks.size(), this.craftingGrid.size()); ++i) {
            final ItemStack gridStack = this.craftingGrid.getStackInSlot(i);
            final ItemStack leftStack = leftStacks.get(i);

            if (!gridStack.isEmpty()) {
                this.craftingGrid.extractItem(i, 1, false);
            }

            if (!leftStack.isEmpty()) {
                if (this.craftingGrid.getStackInSlot(i).isEmpty()) {
                    this.craftingGrid.setItemDirect(i, leftStack);
                } else if (!this.player.getInventory().add(leftStack)) {
                    this.player.drop(leftStack, false);
                }
            }
        }
    }

    private ItemStack extractItemsByRecipe(MEStorage src, Level level, Recipe<Container> r, ItemStack output, CraftingContainer ci, ItemStack providedTemplate, int slot, KeyCounter items, IPartitionList filter) {
        if (this.energySrc.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.9) {
            if (providedTemplate == null) {
                return ItemStack.EMPTY;
            }
            var ae_req = AEItemKey.of(providedTemplate);
            if (ae_req != null) {
                if (filter == null || filter.isListed(ae_req)) {
                    var extracted = src.extract(ae_req, 1, Actionable.MODULATE, this.mySrc);
                    if (extracted > 0) {
                        this.energySrc.extractAEPower(1, Actionable.MODULATE, PowerMultiplier.CONFIG);
                        return ae_req.toStack();
                    }
                }
            }
            var checkFuzzy = providedTemplate.hasTag() || providedTemplate.isDamageableItem();
            if (items != null && checkFuzzy) {
                for (var x : items) {
                    if (x.getKey() instanceof AEItemKey) {
                        AEItemKey itemKey = (AEItemKey) x.getKey();
                        if (providedTemplate.getItem() == itemKey.getItem() && !itemKey.matches(output)) {
                            ci.setItem(slot, itemKey.toStack());
                            if (r.matches(ci, level) && ItemStack.matches(r.assemble(ci, level.registryAccess()), output)) {
                                if (filter == null || filter.isListed(itemKey)) {
                                    var ex = src.extract(itemKey, 1, Actionable.MODULATE, this.mySrc);
                                    if (ex > 0) {
                                        this.energySrc.extractAEPower(1, Actionable.MODULATE, PowerMultiplier.CONFIG);
                                        return itemKey.toStack();
                                    }
                                }
                            }
                            ci.setItem(slot, providedTemplate);
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack remove(int par1) {
        if (this.hasItem()) {
            this.amountCrafted += Math.min(par1, this.getItem().getCount());
        }
        return super.remove(par1);
    }

    @SuppressWarnings("unchecked")
    protected NonNullList<ItemStack> getRemainingItems(Container ic, Level level) {
        if (this.type == null) {
            return NonNullList.withSize(this.craftingGrid.size(), ItemStack.EMPTY);
        }
        Recipe<Container> recipe = this.menu.getCurrentRecipe();
        if (recipe != null && recipe.matches(ic, level)) {
            return recipe.getRemainingItems(ic);
        }
        return level.getRecipeManager().getRecipeFor((RecipeType<Recipe<Container>>) this.type, ic, level)
                .map(r -> r.getRemainingItems(ic))
                .orElse(NonNullList.withSize(this.craftingGrid.size(), ItemStack.EMPTY));
    }

    @SuppressWarnings("unchecked")
    protected Recipe<Container> findRecipe(Container ic, Level level) {
        if (this.type == null) {
            return null;
        }
        Recipe<Container> recipe = this.menu.getCurrentRecipe();
        if (recipe != null && recipe.matches(ic, level)) {
            return recipe;
        }
        return level.getRecipeManager().getRecipeFor((RecipeType<Recipe<Container>>) this.type, ic, level).orElse(null);
    }

}
