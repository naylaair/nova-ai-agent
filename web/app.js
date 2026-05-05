// Nova web demo — same multi-agent pipeline as the Android app, ported to vanilla JS.
//
// 1. Researcher agent: enumerates the key facets to investigate.
// 2. Analyzer agent: critiques the research notes and synthesizes them.
// 3. Writer agent: turns the synthesis into the final user-facing reply.
//
// Each step is a separate Claude call, and the intermediate outputs are shown
// in the chat bubble so the user can audit the chain of thought.

const RESEARCHER_PROMPT = `You are the Researcher agent in a small multi-agent pipeline.
Given a user question, list the key sub-questions, facts and considerations
needed to answer it well. Be concise (max 8 bullets). Do NOT answer the question
yourself. Output bullets only.`;

const ANALYZER_PROMPT = `You are the Analyzer agent. You receive the user question and
the Researcher's notes. Critique the notes, fix any inaccuracies, fill in
important gaps and produce a tight synthesis of the most relevant points. Be
concise. Do NOT write the final answer yet.`;

const WRITER_PROMPT = `You are the Writer agent. Using the analyst synthesis,
write a clear, friendly, well-structured final answer for the end user. Use
short paragraphs and, where helpful, lists. Do not mention the internal
pipeline or other agents.`;

const MODEL = "claude-3-5-sonnet-20241022";
const ENDPOINT = "https://api.anthropic.com/v1/messages";

const STORE_KEY = "nova_anthropic_api_key";

const state = {
    apiKey: localStorage.getItem(STORE_KEY) || "",
    busy: false,
};

const $ = (sel) => document.querySelector(sel);
const main = $("#main");
const empty = $("#empty");
const input = $("#input");
const sendBtn = $("#send");
const modal = $("#modal");
const settingsBtn = $("#settings-btn");
const cancelBtn = $("#cancel");
const saveBtn = $("#save");
const keyInput = $("#key-input");
const errBox = $("#err");

function showError(msg) {
    errBox.textContent = msg;
    errBox.style.display = "block";
    setTimeout(() => { errBox.style.display = "none"; }, 6000);
}

settingsBtn.addEventListener("click", () => {
    keyInput.value = state.apiKey;
    modal.classList.add("open");
    keyInput.focus();
});
cancelBtn.addEventListener("click", () => modal.classList.remove("open"));
saveBtn.addEventListener("click", () => {
    state.apiKey = keyInput.value.trim();
    localStorage.setItem(STORE_KEY, state.apiKey);
    modal.classList.remove("open");
});

input.addEventListener("input", () => {
    sendBtn.disabled = !input.value.trim() || state.busy;
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 120) + "px";
});

input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        if (!sendBtn.disabled) sendBtn.click();
    }
});

sendBtn.addEventListener("click", async () => {
    const prompt = input.value.trim();
    if (!prompt) return;
    if (!state.apiKey) {
        showError("Set your Anthropic API key in settings first.");
        modal.classList.add("open");
        keyInput.focus();
        return;
    }
    input.value = "";
    input.style.height = "auto";
    sendBtn.disabled = true;

    if (empty) empty.remove();

    addBubble("user", prompt);
    const assistant = addBubble("assistant", "");
    const traceEl = document.createElement("div");
    traceEl.className = "trace";
    assistant.bubble.prepend(traceEl);
    const banner = addBanner("Researcher working…");

    state.busy = true;

    try {
        const research = await callClaude(RESEARCHER_PROMPT, prompt, 700);
        appendTraceStep(traceEl, "🔍 Researcher", research);
        banner.textContent = "Analyzer working…";
        addSpinner(banner);

        const analysis = await callClaude(
            ANALYZER_PROMPT,
            `Original user question:\n${prompt}\n\nResearcher notes:\n${research}`,
            700,
        );
        appendTraceStep(traceEl, "🧠 Analyzer", analysis);
        banner.textContent = "Writer working…";
        addSpinner(banner);

        const finalAnswer = await callClaude(
            WRITER_PROMPT,
            `Original user question:\n${prompt}\n\nAnalyst synthesis:\n${analysis}`,
            1024,
        );
        appendTraceStep(traceEl, "✍️ Writer", finalAnswer);
        assistant.body.textContent = finalAnswer;
    } catch (err) {
        showError(err.message || String(err));
        assistant.bubble.remove();
    } finally {
        banner.remove();
        state.busy = false;
        sendBtn.disabled = !input.value.trim();
        main.scrollTop = main.scrollHeight;
    }
});

function addBubble(role, text) {
    const bubble = document.createElement("div");
    bubble.className = `bubble ${role}`;
    const body = document.createElement("div");
    body.textContent = text || "…";
    bubble.appendChild(body);
    main.appendChild(bubble);
    main.scrollTop = main.scrollHeight;
    return { bubble, body };
}

function addBanner(text) {
    const b = document.createElement("div");
    b.className = "banner";
    b.textContent = text;
    addSpinner(b);
    main.appendChild(b);
    main.scrollTop = main.scrollHeight;
    return b;
}

function addSpinner(el) {
    el.querySelectorAll(".spinner").forEach((s) => s.remove());
    const sp = document.createElement("span");
    sp.className = "spinner";
    el.prepend(sp);
}

function appendTraceStep(traceEl, role, text) {
    const step = document.createElement("div");
    step.className = "step";
    const r = document.createElement("span");
    r.className = "role";
    r.textContent = role;
    step.appendChild(r);
    step.appendChild(document.createTextNode(text.trim()));
    traceEl.appendChild(step);
    main.scrollTop = main.scrollHeight;
}

async function callClaude(system, user, maxTokens) {
    const res = await fetch(ENDPOINT, {
        method: "POST",
        headers: {
            "content-type": "application/json",
            "x-api-key": state.apiKey,
            "anthropic-version": "2023-06-01",
            "anthropic-dangerous-direct-browser-access": "true",
        },
        body: JSON.stringify({
            model: MODEL,
            max_tokens: maxTokens,
            system,
            messages: [{ role: "user", content: user }],
        }),
    });
    if (!res.ok) {
        let detail = `${res.status} ${res.statusText}`;
        try {
            const body = await res.json();
            if (body?.error?.message) detail = body.error.message;
        } catch {}
        throw new Error(`Claude API error: ${detail}`);
    }
    const data = await res.json();
    const block = data.content?.find((b) => b.type === "text");
    if (!block?.text) throw new Error("Empty response from Claude");
    return block.text;
}
