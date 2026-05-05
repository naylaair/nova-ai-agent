package com.nova.agent.agents

import com.nova.agent.api.ClaudeClient

enum class AgentRole(val displayName: String, val emoji: String) {
    RESEARCHER("Researcher", "\uD83D\uDD0D"),
    ANALYZER("Analyzer", "\uD83E\uDDE0"),
    WRITER("Writer", "\u270D\uFE0F");
}

data class AgentStep(
    val role: AgentRole,
    val output: String,
)

data class AgentResult(
    val steps: List<AgentStep>,
    val finalAnswer: String,
)

/**
 * A small multi-agent pipeline that mimics long-chain reasoning:
 *
 * 1. Researcher agent expands the user prompt into key facets to investigate.
 * 2. Analyzer agent critiques and synthesizes the research notes.
 * 3. Writer agent composes the final, user-facing answer.
 *
 * Each step is sequential and feeds into the next. Intermediate outputs are
 * surfaced to the UI so the user can audit the chain of thought.
 */
class AgentOrchestrator(private val client: ClaudeClient) {

    suspend fun run(
        apiKey: String,
        userPrompt: String,
        onStepUpdate: (List<AgentStep>, AgentRole) -> Unit,
    ): AgentResult {
        val steps = mutableListOf<AgentStep>()

        onStepUpdate(steps.toList(), AgentRole.RESEARCHER)
        val research = client.complete(
            apiKey = apiKey,
            system = RESEARCHER_PROMPT,
            userPrompt = userPrompt,
            maxTokens = 700,
        )
        steps += AgentStep(AgentRole.RESEARCHER, research)
        onStepUpdate(steps.toList(), AgentRole.ANALYZER)

        val analysis = client.complete(
            apiKey = apiKey,
            system = ANALYZER_PROMPT,
            userPrompt = buildString {
                append("Original user question:\n")
                append(userPrompt)
                append("\n\nResearcher notes:\n")
                append(research)
            },
            maxTokens = 700,
        )
        steps += AgentStep(AgentRole.ANALYZER, analysis)
        onStepUpdate(steps.toList(), AgentRole.WRITER)

        val finalAnswer = client.complete(
            apiKey = apiKey,
            system = WRITER_PROMPT,
            userPrompt = buildString {
                append("Original user question:\n")
                append(userPrompt)
                append("\n\nAnalyst synthesis:\n")
                append(analysis)
            },
            maxTokens = 1024,
        )
        steps += AgentStep(AgentRole.WRITER, finalAnswer)
        onStepUpdate(steps.toList(), AgentRole.WRITER)

        return AgentResult(steps = steps.toList(), finalAnswer = finalAnswer)
    }

    companion object {
        private const val RESEARCHER_PROMPT = """
            You are the Researcher agent in a small multi-agent pipeline.
            Given a user question, list the key sub-questions, facts and
            considerations needed to answer it well. Be concise (max 8 bullets).
            Do NOT answer the question yourself. Output bullets only.
        """

        private const val ANALYZER_PROMPT = """
            You are the Analyzer agent. You receive the user question and the
            Researcher's notes. Critique the notes, fix any inaccuracies, fill
            in important gaps and produce a tight synthesis of the most
            relevant points. Be concise. Do NOT write the final answer yet.
        """

        private const val WRITER_PROMPT = """
            You are the Writer agent. Using the analyst synthesis, write a
            clear, friendly, well-structured final answer for the end user.
            Use short paragraphs and, where helpful, lists. Do not mention the
            internal pipeline or other agents.
        """
    }
}
