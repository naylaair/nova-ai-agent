package com.nova.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nova.agent.agents.AgentOrchestrator
import com.nova.agent.agents.AgentRole
import com.nova.agent.agents.AgentStep
import com.nova.agent.api.ClaudeClient
import com.nova.agent.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val steps: List<AgentStep> = emptyList(),
    val isStreaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT }
}

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val activeAgent: AgentRole? = null,
    val apiKey: String = "",
    val showSettings: Boolean = false,
    val errorMessage: String? = null,
)

class ChatViewModel(context: Context) : ViewModel() {

    private val settings = SettingsStore(context)
    private val client = ClaudeClient()
    private val orchestrator = AgentOrchestrator(client)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val key = settings.apiKeyFlow.first()
            _state.update { it.copy(apiKey = key) }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            settings.setApiKey(key)
            _state.update { it.copy(apiKey = key) }
        }
    }

    fun toggleSettings() {
        _state.update { it.copy(showSettings = !it.showSettings) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun send(prompt: String) {
        val key = _state.value.apiKey
        if (key.isBlank()) {
            _state.update { it.copy(errorMessage = "Set your Anthropic API key in settings first.") }
            return
        }
        if (prompt.isBlank() || _state.value.isThinking) return

        val userMsg = ChatMessage(
            id = System.currentTimeMillis(),
            role = ChatMessage.Role.USER,
            content = prompt.trim(),
        )
        val assistantMsg = ChatMessage(
            id = userMsg.id + 1,
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            isStreaming = true,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                isThinking = true,
                activeAgent = AgentRole.RESEARCHER,
            )
        }

        viewModelScope.launch {
            runCatching {
                orchestrator.run(
                    apiKey = key,
                    userPrompt = prompt.trim(),
                    onStepUpdate = { steps, currentAgent ->
                        _state.update { s ->
                            s.copy(
                                activeAgent = currentAgent,
                                messages = s.messages.map {
                                    if (it.id == assistantMsg.id) it.copy(steps = steps) else it
                                },
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                _state.update { s ->
                    s.copy(
                        isThinking = false,
                        activeAgent = null,
                        messages = s.messages.map {
                            if (it.id == assistantMsg.id) {
                                it.copy(
                                    content = result.finalAnswer,
                                    steps = result.steps,
                                    isStreaming = false,
                                )
                            } else it
                        },
                    )
                }
            }.onFailure { err ->
                _state.update { s ->
                    s.copy(
                        isThinking = false,
                        activeAgent = null,
                        errorMessage = err.message ?: "Unknown error",
                        messages = s.messages.filter { it.id != assistantMsg.id },
                    )
                }
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(context) as T
        }
    }
}
