package com.glodblock.github.extendedae.common.me.taglist;

import com.glodblock.github.extendedae.ExtendedAE;
import com.glodblock.github.extendedae.config.EPPConfig;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TagExpParser {

    private static final LoadingCache<String, Predicate<Set<String>>> COMPILED_EXPRESSION_CACHE = CacheBuilder.newBuilder()
            .maximumSize(512)
            .build(new CacheLoader<String, Predicate<Set<String>>>() {
                @Override
                public @NotNull Predicate<Set<String>> load(@NotNull String key) {
                    return compileInternal(key);
                }
            });

    public static Predicate<Set<String>> compile(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return tags -> false;
        }
        return COMPILED_EXPRESSION_CACHE.getUnchecked(washExpression(expression));
    }

    private static String washExpression(String expression) {
        expression = expression.replace("&&", "&");
        expression = expression.replace("||", "|");
        return expression;
    }

    @SuppressWarnings("deprecation")
    public static boolean evaluate(Predicate<Set<String>> predicate, Object key) {
        Holder<?> holder = null;
        Holder<?> associateHolder = null;
        if (key instanceof Item) {
            Item item = (Item) key;
            holder = item.builtInRegistryHolder();
            if (item instanceof BlockItem) {
                BlockItem block = (BlockItem) item;
                associateHolder = block.getBlock().builtInRegistryHolder();
            }
        } else if (key instanceof Fluid) {
            Fluid fluid = (Fluid) key;
            holder = fluid.builtInRegistryHolder();
        }

        if (holder != null) {
            Set<String> tagStrings = Stream.concat(holder.tags(), associateHolder == null ? Stream.empty() : associateHolder.tags())
                    .map(tagKey -> tagKey.location().toString())
                    .collect(Collectors.toSet());
            holder.unwrapKey().ifPresent(resourceKey -> tagStrings.add(resourceKey.location().toString()));
            return predicate.test(tagStrings);
        }

        return false;
    }

    private static Predicate<Set<String>> compileInternal(String expression) {
        try {
            List<Token> tokens = tokenize(expression);
            Queue<Token> rpn = convertToRPN(tokens);
            return actualTags -> {
                try {
                    return evaluateRPN(rpn, actualTags);
                } catch (IllegalArgumentException e) {
                    if (EPPConfig.debugMode) {
                        ExtendedAE.LOGGER.error("Failed to evaluate RPN in expression: '{}' - {}", expression, e.getMessage());
                    }
                    return false;
                }
            };
        } catch (IllegalArgumentException e) {
            if (EPPConfig.debugMode) {
                ExtendedAE.LOGGER.error("Failed to parse tag expression: '{}' - {}", expression, e.getMessage());
            }
            return tags -> false;
        }
    }

    private enum TokenType {
        TAG,
        OPERATOR,
        LPAREN,
        RPAREN
    }

    private static final class Token {
        final TokenType type;
        final String value;
        final Operator op;

        Token(TokenType type, String value, Operator op) {
            this.type = type;
            this.value = value;
            this.op = op;
        }

        Token(TokenType type, String value) {
            this(type, value, null);
        }

        Token(Operator op) {
            this(TokenType.OPERATOR, op.symbol, op);
        }
    }

    private enum Operator {
        NOT("!", 3, true),
        AND("&", 2, false),
        XOR("^", 1, false),
        OR("|", 0, false);

        final String symbol;
        final int precedence;
        final boolean rightAssociative;

        Operator(String symbol, int precedence, boolean rightAssociative) {
            this.symbol = symbol;
            this.precedence = precedence;
            this.rightAssociative = rightAssociative;
        }

        static Operator fromSymbol(char symbol) {
            for (Operator op : values()) {
                if (op.symbol.charAt(0) == symbol) {
                    return op;
                }
            }
            return null;
        }
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder currentTag = new StringBuilder();
        boolean expectingOperand = true;
        boolean lastIsTag = false;
        int lp = 0;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            Operator op = Operator.fromSymbol(c);

            if (c == '(') {
                if (!expectingOperand) {
                    throw new IllegalArgumentException("Unexpected '(' at position " + i + ". Expected operator or ')'.");
                }
                flushTag(currentTag, tokens);
                tokens.add(new Token(TokenType.LPAREN, "("));
                expectingOperand = true;
                lp++;
                lastIsTag = false;
            } else if (c == ')') {
                if (expectingOperand && lp <= 0) {
                    throw new IllegalArgumentException("Unexpected ')' at position " + i + ". Expected operand or '('.");
                }
                flushTag(currentTag, tokens);
                tokens.add(new Token(TokenType.RPAREN, ")"));
                expectingOperand = false;
                lp--;
                lastIsTag = false;
            } else if (op != null) {
                if (op == Operator.NOT && expectingOperand) {
                    flushTag(currentTag, tokens);
                    tokens.add(new Token(op));
                    expectingOperand = true;
                } else if (op != Operator.NOT) {
                    if (lastIsTag || !expectingOperand) {
                        flushTag(currentTag, tokens);
                        tokens.add(new Token(op));
                        expectingOperand = true;
                    }
                } else {
                    throw new IllegalArgumentException("Unexpected operator '" + c + "' at position " + i + ".");
                }
                lastIsTag = false;
            } else {
                if (!expectingOperand) {
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + i + ". Expected operator or ')'.");
                }
                currentTag.append(c);
                lastIsTag = true;
            }
        }

        flushTag(currentTag, tokens);

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be empty.");
        }

        if (lp > 0) {
            throw new IllegalArgumentException("Missing ')' at the end of the expression.");
        }

        if (expectingOperand
                && tokens.get(tokens.size() - 1).type != TokenType.TAG
                && tokens.get(tokens.size() - 1).type != TokenType.RPAREN) {
            throw new IllegalArgumentException("Expression ended unexpectedly. Expected operand after last token.");
        }

        return tokens;
    }

    private static void flushTag(StringBuilder currentTag, List<Token> tokens) {
        if (currentTag.length() > 0) {
            tokens.add(new Token(TokenType.TAG, currentTag.toString()));
            currentTag.setLength(0);
        }
    }

    private static Queue<Token> convertToRPN(List<Token> tokens) {
        Queue<Token> outputQueue = new LinkedList<>();
        Deque<Token> operatorStack = new ArrayDeque<>();

        for (Token token : tokens) {
            switch (token.type) {
                case TAG:
                    outputQueue.offer(token);
                    break;
                case OPERATOR:
                    while (!operatorStack.isEmpty() && operatorStack.peek().type == TokenType.OPERATOR) {
                        Token topOpToken = operatorStack.peek();
                        Operator currentOp = token.op;
                        Operator topOp = topOpToken.op;
                        if ((!currentOp.rightAssociative && currentOp.precedence <= topOp.precedence)
                                || (currentOp.rightAssociative && currentOp.precedence < topOp.precedence)) {
                            outputQueue.offer(operatorStack.pop());
                        } else {
                            break;
                        }
                    }
                    operatorStack.push(token);
                    break;
                case LPAREN:
                    operatorStack.push(token);
                    break;
                case RPAREN:
                    boolean foundParen = false;
                    while (!operatorStack.isEmpty()) {
                        Token topToken = operatorStack.peek();
                        if (topToken.type == TokenType.LPAREN) {
                            operatorStack.pop();
                            foundParen = true;
                            break;
                        } else {
                            outputQueue.offer(operatorStack.pop());
                        }
                    }
                    if (!foundParen) {
                        throw new IllegalArgumentException("Mismatched parentheses: Closing parenthesis without matching opening parenthesis.");
                    }
                    break;
                default:
                    break;
            }
        }

        while (!operatorStack.isEmpty()) {
            Token topToken = operatorStack.peek();
            if (topToken.type == TokenType.LPAREN) {
                throw new IllegalArgumentException("Mismatched parentheses: Opening parenthesis without matching closing parenthesis.");
            }
            if (topToken.type == TokenType.OPERATOR) {
                outputQueue.offer(operatorStack.pop());
            } else {
                throw new IllegalStateException("Unexpected token type on operator stack: " + topToken.type);
            }
        }

        return outputQueue;
    }

    private static boolean evaluateRPN(Queue<Token> rpnQueue, Set<String> actualTags) {
        Deque<Boolean> valueStack = new ArrayDeque<>();
        Queue<Token> queueCopy = new LinkedList<>(rpnQueue);

        while (!queueCopy.isEmpty()) {
            Token token = queueCopy.poll();

            if (token.type == TokenType.TAG) {
                boolean match = actualTags.stream().anyMatch(tag -> matchesWildcard(token.value, tag));
                valueStack.push(match);
            } else if (token.type == TokenType.OPERATOR) {
                Operator op = token.op;
                try {
                    if (op == Operator.NOT) {
                        if (valueStack.isEmpty()) {
                            throw new IllegalArgumentException("Invalid expression: NOT operator requires one operand.");
                        }
                        boolean operand = valueStack.pop();
                        valueStack.push(!operand);
                    } else {
                        if (valueStack.size() < 2) {
                            throw new IllegalArgumentException("Invalid expression: Binary operator '" + op.symbol + "' requires two operands.");
                        }
                        boolean right = valueStack.pop();
                        boolean left = valueStack.pop();
                        switch (op) {
                            case AND:
                                valueStack.push(left && right);
                                break;
                            case OR:
                                valueStack.push(left || right);
                                break;
                            case XOR:
                                valueStack.push(left ^ right);
                                break;
                            default:
                                throw new IllegalStateException("Unexpected binary operator: " + op);
                        }
                    }
                } catch (NoSuchElementException e) {
                    throw new IllegalArgumentException("Invalid RPN expression: Not enough operands for operator '" + op.symbol + "'.");
                }
            } else {
                throw new IllegalStateException("Unexpected token type in RPN queue: " + token.type);
            }
        }

        if (valueStack.size() == 1) {
            return valueStack.pop();
        }
        if (valueStack.isEmpty() && rpnQueue.isEmpty()) {
            return false;
        }
        throw new IllegalArgumentException("Invalid RPN expression: Evaluation finished with " + valueStack.size() + " values on the stack (expected 1).");
    }

    private static boolean matchesWildcard(@NotNull String pattern, @NotNull String text) {
        if (pattern.equals("*") || pattern.equals(text)) {
            return true;
        }

        String regex = pattern
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("?", "\\?")
                .replace("+", "\\+")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("|", "\\|")
                .replace("*", ".*");

        return text.matches(regex);
    }
}
