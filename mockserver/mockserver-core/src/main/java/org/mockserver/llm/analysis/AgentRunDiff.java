package org.mockserver.llm.analysis;

import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.PromptNormalizer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NormalizationOptions;
import org.mockserver.model.Provider;
import org.mockserver.model.ToolUse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, prompt-level diff of two recorded agent runs. Each run is
 * reconstructed into its canonical {@link ParsedConversation} (via
 * {@link AgentRunAnalyzer#canonicalConversation}), every message's text is
 * normalised through {@link PromptNormalizer} (so cosmetic churn — whitespace,
 * JSON key order, volatile ids/timestamps — is not reported as a change), then
 * the two message sequences are aligned with a longest-common-subsequence diff.
 * <p>
 * Surfaces what changed between the runs: message-level additions / removals /
 * edits, the tool calls that were added or removed, and the token / cost delta
 * (when both sides carry usage totals, e.g. supplied from the two sides'
 * optimisation reports).
 * <p>
 * Pure (no network, no LLM). The caller is responsible for redacting the
 * requests before passing them in when the diff will be surfaced to a user.
 */
public class AgentRunDiff {

    public enum ChangeType {
        UNCHANGED,
        ADDED,
        REMOVED,
        CHANGED
    }

    /** A single message-level difference between the two runs. */
    public static final class MessageDiff {
        private final ChangeType changeType;
        private final String role;
        private final String beforeText;
        private final String afterText;

        MessageDiff(ChangeType changeType, String role, String beforeText, String afterText) {
            this.changeType = changeType;
            this.role = role;
            this.beforeText = beforeText;
            this.afterText = afterText;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public String getRole() {
            return role;
        }

        /** The normalised before-run text (null when the message was ADDED). */
        public String getBeforeText() {
            return beforeText;
        }

        /** The normalised after-run text (null when the message was REMOVED). */
        public String getAfterText() {
            return afterText;
        }
    }

    /** Token / cost deltas (after − before). Present only when both sides carry totals. */
    public static final class TokenDelta {
        private final long inputTokensBefore;
        private final long inputTokensAfter;
        private final long outputTokensBefore;
        private final long outputTokensAfter;
        private final double costUsdBefore;
        private final double costUsdAfter;

        TokenDelta(long inputTokensBefore, long inputTokensAfter, long outputTokensBefore,
                   long outputTokensAfter, double costUsdBefore, double costUsdAfter) {
            this.inputTokensBefore = inputTokensBefore;
            this.inputTokensAfter = inputTokensAfter;
            this.outputTokensBefore = outputTokensBefore;
            this.outputTokensAfter = outputTokensAfter;
            this.costUsdBefore = costUsdBefore;
            this.costUsdAfter = costUsdAfter;
        }

        public long getInputTokensBefore() {
            return inputTokensBefore;
        }

        public long getInputTokensAfter() {
            return inputTokensAfter;
        }

        public long getInputTokensDelta() {
            return inputTokensAfter - inputTokensBefore;
        }

        public long getOutputTokensBefore() {
            return outputTokensBefore;
        }

        public long getOutputTokensAfter() {
            return outputTokensAfter;
        }

        public long getOutputTokensDelta() {
            return outputTokensAfter - outputTokensBefore;
        }

        public double getCostUsdBefore() {
            return costUsdBefore;
        }

        public double getCostUsdAfter() {
            return costUsdAfter;
        }

        public double getCostUsdDelta() {
            return costUsdAfter - costUsdBefore;
        }
    }

    /** The full diff of two runs. */
    public static final class RunDiffResult {
        private final boolean promptChanged;
        private final List<MessageDiff> messageDiffs;
        private final List<String> toolCallsAdded;
        private final List<String> toolCallsRemoved;
        private final int messageCountBefore;
        private final int messageCountAfter;
        private final TokenDelta tokenDelta;

        RunDiffResult(boolean promptChanged, List<MessageDiff> messageDiffs, List<String> toolCallsAdded,
                      List<String> toolCallsRemoved, int messageCountBefore, int messageCountAfter,
                      TokenDelta tokenDelta) {
            this.promptChanged = promptChanged;
            this.messageDiffs = messageDiffs;
            this.toolCallsAdded = toolCallsAdded;
            this.toolCallsRemoved = toolCallsRemoved;
            this.messageCountBefore = messageCountBefore;
            this.messageCountAfter = messageCountAfter;
            this.tokenDelta = tokenDelta;
        }

        /** True when any message-level difference (add/remove/change) was found. */
        public boolean isPromptChanged() {
            return promptChanged;
        }

        public List<MessageDiff> getMessageDiffs() {
            return messageDiffs;
        }

        /** Tool-call fingerprints ({@code name(args)}) present in the after run only. */
        public List<String> getToolCallsAdded() {
            return toolCallsAdded;
        }

        /** Tool-call fingerprints ({@code name(args)}) present in the before run only. */
        public List<String> getToolCallsRemoved() {
            return toolCallsRemoved;
        }

        public int getMessageCountBefore() {
            return messageCountBefore;
        }

        public int getMessageCountAfter() {
            return messageCountAfter;
        }

        /** Token / cost delta, or null when either side did not supply totals. */
        public TokenDelta getTokenDelta() {
            return tokenDelta;
        }
    }

    /** One side of the diff: the recorded requests, the provider, and optional usage totals. */
    public static final class RunSide {
        private final List<HttpRequest> requests;
        private final Provider provider;
        private final Long inputTokens;
        private final Long outputTokens;
        private final Double costUsd;

        public RunSide(List<HttpRequest> requests, Provider provider) {
            this(requests, provider, null, null, null);
        }

        public RunSide(List<HttpRequest> requests, Provider provider,
                       Long inputTokens, Long outputTokens, Double costUsd) {
            this.requests = requests;
            this.provider = provider;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.costUsd = costUsd;
        }

        boolean hasTotals() {
            return inputTokens != null && outputTokens != null && costUsd != null;
        }
    }

    private final AgentRunAnalyzer analyzer = new AgentRunAnalyzer();

    /**
     * Diff the {@code before} run against the {@code after} run, normalising prompt
     * text with {@code options} (null means the {@link NormalizationOptions}
     * defaults).
     */
    public RunDiffResult diff(RunSide before, RunSide after, NormalizationOptions options) {
        NormalizationOptions effectiveOptions = options != null ? options : NormalizationOptions.normalizationOptions();

        List<ParsedMessage> beforeMessages = messagesOf(before);
        List<ParsedMessage> afterMessages = messagesOf(after);

        List<NormalizedMessage> beforeNormalized = normalize(beforeMessages, effectiveOptions);
        List<NormalizedMessage> afterNormalized = normalize(afterMessages, effectiveOptions);

        List<MessageDiff> messageDiffs = diffMessages(beforeNormalized, afterNormalized);
        boolean promptChanged = messageDiffs.stream().anyMatch(d -> d.getChangeType() != ChangeType.UNCHANGED);

        Set<String> beforeTools = toolCallFingerprints(beforeMessages, effectiveOptions);
        Set<String> afterTools = toolCallFingerprints(afterMessages, effectiveOptions);
        List<String> toolCallsAdded = difference(afterTools, beforeTools);
        List<String> toolCallsRemoved = difference(beforeTools, afterTools);

        TokenDelta tokenDelta = null;
        if (before.hasTotals() && after.hasTotals()) {
            tokenDelta = new TokenDelta(before.inputTokens, after.inputTokens,
                before.outputTokens, after.outputTokens, before.costUsd, after.costUsd);
        }

        return new RunDiffResult(promptChanged, messageDiffs, toolCallsAdded, toolCallsRemoved,
            beforeMessages.size(), afterMessages.size(), tokenDelta);
    }

    private List<ParsedMessage> messagesOf(RunSide side) {
        if (side == null || side.requests == null || side.provider == null) {
            return new ArrayList<>();
        }
        ParsedConversation conversation = analyzer.canonicalConversation(side.requests, side.provider);
        return conversation != null ? new ArrayList<>(conversation.getMessages()) : new ArrayList<>();
    }

    private List<NormalizedMessage> normalize(List<ParsedMessage> messages, NormalizationOptions options) {
        List<NormalizedMessage> result = new ArrayList<>();
        for (ParsedMessage message : messages) {
            String role = message.getRole().name();
            String text = message.getTextContent();
            if ((text == null || text.isEmpty()) && message.getRole() == ParsedMessage.Role.TOOL) {
                text = String.join("\n", message.getToolResults().values());
            }
            String normalized = LlmOptimisationBriefRenderer.maskSecrets(
                PromptNormalizer.normalize(text != null ? text : "", options));
            result.add(new NormalizedMessage(role, normalized));
        }
        return result;
    }

    /**
     * Longest-common-subsequence alignment of the two normalised message lists,
     * emitting UNCHANGED / REMOVED / ADDED ops and then coalescing an adjacent
     * REMOVED+ADDED of the same role into a single CHANGED.
     */
    private List<MessageDiff> diffMessages(List<NormalizedMessage> before, List<NormalizedMessage> after) {
        int n = before.size();
        int m = after.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (before.get(i).key().equals(after.get(j).key())) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<MessageDiff> raw = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            NormalizedMessage b = before.get(i);
            NormalizedMessage a = after.get(j);
            if (b.key().equals(a.key())) {
                raw.add(new MessageDiff(ChangeType.UNCHANGED, b.role, b.text, a.text));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                raw.add(new MessageDiff(ChangeType.REMOVED, b.role, b.text, null));
                i++;
            } else {
                raw.add(new MessageDiff(ChangeType.ADDED, a.role, null, a.text));
                j++;
            }
        }
        while (i < n) {
            NormalizedMessage b = before.get(i++);
            raw.add(new MessageDiff(ChangeType.REMOVED, b.role, b.text, null));
        }
        while (j < m) {
            NormalizedMessage a = after.get(j++);
            raw.add(new MessageDiff(ChangeType.ADDED, a.role, null, a.text));
        }

        return coalesceChanges(raw);
    }

    /** Merge an immediate REMOVED followed by an ADDED of the same role into a CHANGED. */
    private List<MessageDiff> coalesceChanges(List<MessageDiff> raw) {
        List<MessageDiff> result = new ArrayList<>();
        for (int k = 0; k < raw.size(); k++) {
            MessageDiff current = raw.get(k);
            if (current.getChangeType() == ChangeType.REMOVED && k + 1 < raw.size()) {
                MessageDiff next = raw.get(k + 1);
                if (next.getChangeType() == ChangeType.ADDED && next.getRole().equals(current.getRole())) {
                    result.add(new MessageDiff(ChangeType.CHANGED, current.getRole(),
                        current.getBeforeText(), next.getAfterText()));
                    k++; // consume the ADDED
                    continue;
                }
            }
            result.add(current);
        }
        return result;
    }

    private Set<String> toolCallFingerprints(List<ParsedMessage> messages, NormalizationOptions options) {
        Set<String> fingerprints = new LinkedHashSet<>();
        for (ParsedMessage message : messages) {
            if (message.getRole() != ParsedMessage.Role.ASSISTANT) {
                continue;
            }
            for (ToolUse toolCall : message.getToolCalls()) {
                String name = toolCall.getName() != null ? toolCall.getName() : "";
                String args = toolCall.getArguments() != null ? toolCall.getArguments() : "";
                fingerprints.add(name + "(" + PromptNormalizer.normalize(args, options) + ")");
            }
        }
        return fingerprints;
    }

    private static List<String> difference(Set<String> a, Set<String> b) {
        List<String> result = new ArrayList<>();
        for (String value : a) {
            if (!b.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /** A message reduced to (role, normalised-text) for alignment. */
    private static final class NormalizedMessage {
        private final String role;
        private final String text;

        NormalizedMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }

        String key() {
            return role + "\u0000" + text;
        }
    }
}
