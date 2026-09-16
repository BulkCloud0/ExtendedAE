package com.glodblock.github.extendedae.common.me.taglist;

import appeng.api.stacks.AEKey;
import appeng.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import lombok.var;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public class TagPriorityList implements IPartitionList {

    private static final Map<TagPriorityList, Runnable> INVALIDATOR = new WeakHashMap<>();
    private final String rawWhiteListExpression;
    private final String rawBlackListExpression;
    private final Predicate<Set<String>> whiteListPredicate;
    private final Predicate<Set<String>> blackListPredicate;
    private final boolean isWhitelistActive;
    private final Reference2BooleanMap<Object> memory = new Reference2BooleanOpenHashMap<>();

    public TagPriorityList(String whiteListExpression, String blackListExpression) {
        this.rawWhiteListExpression = this.removeBlank(whiteListExpression != null ? whiteListExpression : "");
        this.rawBlackListExpression = this.removeBlank(blackListExpression != null ? blackListExpression : "");
        this.whiteListPredicate = TagExpParser.compile(this.rawWhiteListExpression);
        this.blackListPredicate = TagExpParser.compile(this.rawBlackListExpression);
        this.isWhitelistActive = !this.rawWhiteListExpression.trim().isEmpty();
        INVALIDATOR.put(this, this.memory::clear);
    }

    private String removeBlank(String raw) {
        return raw.replaceAll("\\s+", "");
    }

    public static void reset() {
        for (var e : INVALIDATOR.entrySet()) {
            e.getValue().run();
        }
    }

    @Override
    public boolean isListed(AEKey input) {
        if (this.isEmpty()) {
            return true;
        }
        return this.memory.computeIfAbsent(input.getPrimaryKey(), this::eval);
    }

    @Override
    public boolean isEmpty() {
        return rawWhiteListExpression.trim().isEmpty() && rawBlackListExpression.trim().isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return Collections.emptyList();
    }

    private boolean eval(@NotNull Object input) {
        final boolean whiteMatches = TagExpParser.evaluate(this.whiteListPredicate, input);
        final boolean blackMatches = TagExpParser.evaluate(this.blackListPredicate, input);

        if (this.isWhitelistActive) {
            return whiteMatches && !blackMatches;
        } else {
            return !blackMatches;
        }
    }

}
