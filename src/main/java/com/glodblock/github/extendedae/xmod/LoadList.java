package com.glodblock.github.extendedae.xmod;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraftforge.fml.ModList;

public final class LoadList {

    public static boolean JEI = false;
    public static boolean REI = false;
    public static boolean GT = false;

    public static Set<String> MOD_NAME = ModList.get().getMods().stream()
            .flatMap(x -> Stream.of(x.getModId(), x.getDisplayName()))
            .collect(Collectors.toSet());

    private LoadList() {
    }

    public static void init() {
        ModList list = ModList.get();
        JEI = list.isLoaded("jei");
        REI = list.isLoaded("roughlyenoughitems");
        GT = list.isLoaded("gtceu");
    }
}
