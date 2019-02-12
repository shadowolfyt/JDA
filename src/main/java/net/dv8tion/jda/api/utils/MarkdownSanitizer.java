/*
 * Copyright 2015-2019 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api.utils;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

public class MarkdownSanitizer
{
    public static final int NORMAL =     0;
    public static final int BOLD =      1 << 0; // **x**
    public static final int ITALICS_U = 1 << 1; // _x_
    public static final int ITALICS_A = 1 << 2; // *x*
    public static final int MONO =      1 << 3; // `x`
    public static final int MONO_TWO =  1 << 4; // ``x``
    public static final int BLOCK =     1 << 5; // ```x```
    public static final int SPOILER =   1 << 6; // ||x||
    public static final int UNDERLINE = 1 << 7; // __x__
    public static final int STRIKE =    1 << 8; // ~~x~~

    public static final int ESCAPED_BOLD      = Integer.MIN_VALUE | BOLD;
    public static final int ESCAPED_ITALICS_U = Integer.MIN_VALUE | ITALICS_U;
    public static final int ESCAPED_ITALICS_A = Integer.MIN_VALUE | ITALICS_A;
    public static final int ESCAPED_MONO      = Integer.MIN_VALUE | MONO;
    public static final int ESCAPED_MONO_TWO  = Integer.MIN_VALUE | MONO_TWO;
    public static final int ESCAPED_BLOCK     = Integer.MIN_VALUE | BLOCK;
    public static final int ESCAPED_SPOILER   = Integer.MIN_VALUE | SPOILER;
    public static final int ESCAPED_UNDERLINE = Integer.MIN_VALUE | UNDERLINE;
    public static final int ESCAPED_STRIKE    = Integer.MIN_VALUE | STRIKE;

    private static final TIntObjectMap<String> tokens;
    static
    {
        tokens = new TIntObjectHashMap<>();
        tokens.put(NORMAL, "");
        tokens.put(BOLD, "**");
        tokens.put(ITALICS_U, "_");
        tokens.put(ITALICS_A, "*");
        tokens.put(MONO, "`");
        tokens.put(MONO_TWO, "``");
        tokens.put(BLOCK, "```");
        tokens.put(SPOILER, "||");
        tokens.put(UNDERLINE, "__");
        tokens.put(STRIKE, "~~");
    }

    private int ignored = 0;
    private SanitizationStrategy strategy = SanitizationStrategy.REMOVE;

    public MarkdownSanitizer() {}

    public MarkdownSanitizer(int ignored, SanitizationStrategy strategy)
    {
        this.ignored = ignored;
        this.strategy = strategy;
    }

    public static String sanitize(String sequence)
    {
        return sanitize(sequence, SanitizationStrategy.REMOVE);
    }

    public static String sanitize(String sequence, SanitizationStrategy strategy)
    {
        return new MarkdownSanitizer().withStrategy(strategy).compute(sequence);
    }

    public MarkdownSanitizer withStrategy(SanitizationStrategy strategy)
    {
        this.strategy = strategy;
        return this;
    }

    public MarkdownSanitizer withIgnored(int ignored)
    {
        this.ignored |= ignored;
        return this;
    }

    private int getRegion(int index, String sequence) //TODO: Handle escape?
    {
        if (sequence.length() - index >= 4)
        {
            String fourChars = sequence.substring(index, index + 4);
            if (fourChars.equals("\\```"))
                return ESCAPED_BLOCK;
            else if (fourChars.equals("\\***"))
                return ESCAPED_BOLD | ITALICS_A;
        }
        if (sequence.length() - index >= 3)
        {
            String threeChars = sequence.substring(index, index + 3);
            switch (threeChars)
            {
                case "```":
                    return BLOCK;
                case "***":
                    return BOLD | ITALICS_A;
                case "\\**":
                    return ESCAPED_BOLD;
                case "\\__":
                    return ESCAPED_UNDERLINE;
                case "\\~~":
                    return ESCAPED_STRIKE;
                case "\\``":
                    return ESCAPED_MONO_TWO;
                case "\\||":
                    return ESCAPED_SPOILER;
            }
        }
        if (sequence.length() - index >= 2)
        {
            String twoChars = sequence.substring(index, index + 2);
            switch (twoChars)
            {
                case "**":
                    return BOLD;
                case "__":
                    return UNDERLINE;
                case "~~":
                    return STRIKE;
                case "``":
                    return MONO_TWO;
                case "||":
                    return SPOILER;
                case "\\*":
                    return ESCAPED_ITALICS_A;
                case "\\_":
                    return ESCAPED_ITALICS_U;
                case "\\`":
                    return ESCAPED_MONO;
            }
        }
        char current = sequence.charAt(index);
        switch (current)
        {
            case '*':
                return ITALICS_A;
            case '_':
                return ITALICS_U;
            case '`':
                return MONO;
        }
        return NORMAL;
    }

    public int findEndIndex(int afterIndex, int region, String sequence)
    {
        if (isEscape(region))
            return -1;
        int lastMatch = afterIndex;
        while (lastMatch != -1)
        {
            switch (region)
            {
                case BOLD | ITALICS_A:
                    lastMatch = sequence.indexOf("***", lastMatch);
                    break;
                case BOLD:
                    lastMatch = sequence.indexOf("**", lastMatch);
                    break;
                case ITALICS_A:
                    lastMatch = sequence.indexOf('*', lastMatch);
                    break;
                case ITALICS_U:
                    lastMatch = sequence.indexOf('_', lastMatch);
                    break;
                case UNDERLINE:
                    lastMatch = sequence.indexOf("__", lastMatch);
                    break;
                case SPOILER:
                    lastMatch = sequence.indexOf("||", lastMatch);
                    break;
                case MONO:
                    lastMatch = sequence.indexOf('`', lastMatch);
                    break;
                case MONO_TWO:
                    lastMatch = sequence.indexOf("``", lastMatch);
                    break;
                case BLOCK:
                    lastMatch = sequence.indexOf("```", lastMatch);
                    break;
                default:
                    return -1;
            }
            if (lastMatch == -1 || sequence.charAt(lastMatch - 1) != '\\')
                return lastMatch;
            lastMatch++;
        }
        return -1;
    }

    private String handleRegion(int start, int end, String sequence, int region)
    {
        String resolved = sequence.substring(start, end);
        switch (region)
        {
            case BLOCK:
            case MONO:
                return resolved;
            case MONO_TWO:
                return new MarkdownSanitizer(ignored | MONO, strategy).compute(resolved);
            default:
                return sanitize(resolved);
        }
    }

    private int getDelta(int region)
    {
        switch (region)
        {
            case ESCAPED_BLOCK:
            case ESCAPED_BOLD | ITALICS_A:
                return 4;
            case BLOCK:
            case BOLD | ITALICS_A:
            case ESCAPED_MONO_TWO:
            case ESCAPED_BOLD:
            case ESCAPED_UNDERLINE:
            case ESCAPED_SPOILER:
                return 3;
            case MONO_TWO:
            case BOLD:
            case UNDERLINE:
            case SPOILER:
            case ESCAPED_ITALICS_A:
            case ESCAPED_ITALICS_U:
            case ESCAPED_MONO:
                return 2;
            case ITALICS_A:
            case ITALICS_U:
            case MONO:
                return 1;
            default:
                return 0;
        }
    }

    private void applyStrategy(int region, String seq, StringBuilder builder)
    {
        if (strategy == SanitizationStrategy.REMOVE)
        {
            builder.append(seq);
            return;
        }
        String token = tokens.get(region);
        if (token == null)
            return;
        builder.append("\\").append(token)
               .append(seq)
               .append("\\").append(token);
    }

    private boolean isEscape(int region)
    {
        return (Integer.MIN_VALUE & region) != 0;
    }

    private boolean isIgnored(int nextRegion)
    {
        return (nextRegion & ignored) == nextRegion;
    }

    public String compute(String sequence)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sequence.length();)
        {
            int nextRegion = getRegion(i, sequence);
            if (nextRegion == NORMAL)
            {
                builder.append(sequence.charAt(i++));
                continue;
            }

            int endRegion = findEndIndex(i + 1, nextRegion, sequence);
            if (isIgnored(nextRegion) || endRegion == -1)
            {
                int delta = getDelta(nextRegion);
                for (int j = 0; j < delta; j++)
                    builder.append(sequence.charAt(i++));
                continue;
            }
            int delta = getDelta(nextRegion);
            applyStrategy(nextRegion, handleRegion(i + delta, endRegion, sequence, nextRegion), builder);
            i = endRegion + delta;
        }
        return builder.toString();
    }

    public enum SanitizationStrategy
    {
        REMOVE,
        ESCAPE,
    }
}