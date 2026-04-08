package com.ai.assistance.operit.data.miniapp

import com.ai.assistance.operit.data.model.MiniAppType

/**
 * Defines how a mini-app should be generated.
 * Used as input when the AI agent creates a mini-app.
 */
sealed class MiniAppScaffold {

    /**
     * Generate a mini-app from a natural language prompt.
     * The AI agent will produce HTML, CSS, and JS content.
     */
    data class FromPrompt(
        val prompt: String,
        val name: String,
        val type: MiniAppType = MiniAppType.PERSISTENT,
        val description: String? = null,
        val suggestedPermissions: Set<String> = emptySet()
    ) : MiniAppScaffold()

    /**
     * Create a mini-app from a pre-defined template.
     */
    data class FromTemplate(
        val template: MiniAppTemplate,
        val name: String? = null, // Override template name
        val type: MiniAppType = MiniAppType.PERSISTENT,
        val description: String? = null,
        val customPermissions: Set<String>? = null
    ) : MiniAppScaffold()

    /**
     * Create a mini-app from raw file contents (e.g. imported or manually constructed).
     */
    data class FromFiles(
        val files: Map<String, String>, // fileName -> content
        val name: String,
        val type: MiniAppType = MiniAppType.PERSISTENT,
        val description: String? = null,
        val icon: String? = null,
        val entryFile: String = "index.html",
        val requiredPermissions: Set<String> = emptySet(),
        val webPermissions: Set<String> = emptySet(),
        val metadata: Map<String, String> = emptyMap()
    ) : MiniAppScaffold()
}

/**
 * A pre-defined template for common mini-app patterns.
 */
data class MiniAppTemplate(
    val id: String,
    val name: String,
    val description: String,
    val html: String,
    val css: String = "",
    val javascript: String = "",
    val icon: String? = null,
    val suggestedPermissions: Set<String> = emptySet(),
    val suggestedWebPermissions: Set<String> = emptySet()
)

/**
 * Built-in templates available for quick mini-app creation.
 */
object MiniAppTemplates {

    val BLANK = MiniAppTemplate(
        id = "blank",
        name = "Blank App",
        description = "Start from a blank HTML page",
        html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Mini App</title>
    <style>
        body {
            margin: 0;
            padding: 16px;
            font-family: system-ui, -apple-system, sans-serif;
            background: #f5f5f5;
            color: #333;
        }
    </style>
</head>
<body>
    <h1>Hello World</h1>
    <script>
        // Your code here
    </script>
</body>
</html>""",
        css = "",
        javascript = ""
    )

    val TODO_LIST = MiniAppTemplate(
        id = "todo_list",
        name = "Todo List",
        description = "A simple todo list with localStorage persistence",
        html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Todo List</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: system-ui, -apple-system, sans-serif;
            background: #f5f5f5;
            padding: 16px;
            max-width: 600px;
            margin: 0 auto;
        }
        h1 { font-size: 24px; margin-bottom: 16px; }
        .input-row {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
        }
        input[type="text"] {
            flex: 1;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 8px;
            font-size: 16px;
        }
        button {
            padding: 10px 16px;
            background: #007AFF;
            color: white;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            cursor: pointer;
        }
        button:active { opacity: 0.7; }
        ul { list-style: none; }
        li {
            background: white;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        li.done span {
            text-decoration: line-through;
            color: #999;
        }
        li span { flex: 1; }
        .delete-btn {
            background: #ff3b30;
            padding: 4px 8px;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <h1>📝 Todo List</h1>
    <div class="input-row">
        <input type="text" id="todoInput" placeholder="Add a task...">
        <button onclick="addTodo()">Add</button>
    </div>
    <ul id="todoList"></ul>
    <script>
        let todos = JSON.parse(localStorage.getItem('todos') || '[]');

        function saveTodos() {
            localStorage.setItem('todos', JSON.stringify(todos));
            render();
        }

        function addTodo() {
            const input = document.getElementById('todoInput');
            const text = input.value.trim();
            if (text) {
                todos.push({ id: Date.now(), text, done: false });
                input.value = '';
                saveTodos();
            }
        }

        function toggleTodo(id) {
            const todo = todos.find(t => t.id === id);
            if (todo) todo.done = !todo.done;
            saveTodos();
        }

        function deleteTodo(id) {
            todos = todos.filter(t => t.id !== id);
            saveTodos();
        }

        function render() {
            const list = document.getElementById('todoList');
            list.innerHTML = todos.map(t => `
                <li class="${'$'}{t.done ? 'done' : ''}">
                    <input type="checkbox" ${'$'}{t.done ? 'checked' : ''} onchange="toggleTodo(${'$'}{t.id})">
                    <span>${'$'}{t.text}</span>
                    <button class="delete-btn" onclick="deleteTodo(${'$'}{t.id})">✕</button>
                </li>
            `).join('');
        }

        render();
    </script>
</body>
</html>""",
        css = "",
        javascript = ""
    )

    val CALCULATOR = MiniAppTemplate(
        id = "calculator",
        name = "Calculator",
        description = "A simple calculator app",
        html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Calculator</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: system-ui, -apple-system, sans-serif;
            background: #1a1a2e;
            padding: 16px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
        }
        .calc {
            background: #16213e;
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
            width: 100%;
            max-width: 320px;
        }
        .display {
            background: #0f3460;
            color: white;
            font-size: 32px;
            padding: 16px;
            border-radius: 8px;
            text-align: right;
            margin-bottom: 16px;
            min-height: 60px;
            word-break: break-all;
        }
        .buttons {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 8px;
        }
        button {
            padding: 16px;
            font-size: 20px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            background: #e94560;
            color: white;
        }
        button:active { opacity: 0.7; }
        button.op { background: #0f3460; }
        button.eq { background: #53d769; grid-column: span 2; }
    </style>
</head>
<body>
    <div class="calc">
        <div class="display" id="display">0</div>
        <div class="buttons">
            <button class="op" onclick="clearDisplay()">C</button>
            <button class="op" onclick="appendOp('/')">/</button>
            <button class="op" onclick="appendOp('*')">×</button>
            <button class="op" onclick="backspace()">←</button>
            <button onclick="appendNum('7')">7</button>
            <button onclick="appendNum('8')">8</button>
            <button onclick="appendNum('9')">9</button>
            <button class="op" onclick="appendOp('-')">-</button>
            <button onclick="appendNum('4')">4</button>
            <button onclick="appendNum('5')">5</button>
            <button onclick="appendNum('6')">6</button>
            <button class="op" onclick="appendOp('+')">+</button>
            <button onclick="appendNum('1')">1</button>
            <button onclick="appendNum('2')">2</button>
            <button onclick="appendNum('3')">3</button>
            <button onclick="appendNum('.')">.</button>
            <button onclick="appendNum('0')">0</button>
            <button class="eq" onclick="calculate()">=</button>
        </div>
    </div>
    <script>
        let expression = '';
        function updateDisplay() {
            document.getElementById('display').textContent = expression || '0';
        }
        function appendNum(n) { expression += n; updateDisplay(); }
        function appendOp(op) { expression += ' ' + op + ' '; updateDisplay(); }
        function clearDisplay() { expression = ''; updateDisplay(); }
        function backspace() { expression = expression.trimEnd(); if(expression.endsWith(' ')) expression = expression.slice(0,-2); else expression = expression.slice(0,-1); updateDisplay(); }
        function calculate() { try { expression = String(eval(expression)); } catch { expression = 'Error'; } updateDisplay(); }
    </script>
</body>
</html>""",
        css = "",
        javascript = ""
    )

    val NOTE_TAKER = MiniAppTemplate(
        id = "note_taker",
        name = "Note Taker",
        description = "A simple note-taking app with markdown-style editing",
        html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Notes</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: system-ui, -apple-system, sans-serif;
            background: #fafafa;
            padding: 16px;
            max-width: 600px;
            margin: 0 auto;
        }
        h1 { font-size: 24px; margin-bottom: 12px; }
        .note-list { margin-bottom: 16px; }
        .note-item {
            background: white;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            cursor: pointer;
        }
        .note-item:hover { background: #f0f0f0; }
        .note-item h3 { font-size: 16px; margin-bottom: 4px; }
        .note-item p { font-size: 14px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .note-item .date { font-size: 12px; color: #999; margin-top: 4px; }
        textarea {
            width: 100%;
            min-height: 200px;
            padding: 12px;
            border: 1px solid #ddd;
            border-radius: 8px;
            font-size: 16px;
            font-family: inherit;
            resize: vertical;
            margin-bottom: 8px;
        }
        .note-actions {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
        }
        button {
            padding: 10px 16px;
            border: none;
            border-radius: 8px;
            font-size: 14px;
            cursor: pointer;
        }
        .btn-primary { background: #007AFF; color: white; }
        .btn-secondary { background: #e5e5ea; }
        .btn-danger { background: #ff3b30; color: white; }
        .back-btn { background: none; border: none; font-size: 24px; cursor: pointer; margin-bottom: 12px; }
        input[type="text"] {
            width: 100%;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 8px;
            font-size: 16px;
            margin-bottom: 8px;
        }
    </style>
</head>
<body>
    <div id="notesView">
        <h1>📓 Notes</h1>
        <div class="note-list" id="noteList"></div>
        <button class="btn-primary" onclick="newNote()" style="width:100%">+ New Note</button>
    </div>
    <div id="editorView" style="display:none">
        <button class="back-btn" onclick="showNotes()">←</button>
        <input type="text" id="noteTitle" placeholder="Title">
        <textarea id="noteContent" placeholder="Write your note..."></textarea>
        <div class="note-actions">
            <button class="btn-primary" onclick="saveNote()">Save</button>
            <button class="btn-danger" onclick="deleteCurrentNote()">Delete</button>
        </div>
    </div>
    <script>
        let notes = JSON.parse(localStorage.getItem('notes') || '[]');
        let currentNoteId = null;

        function saveToStorage() {
            localStorage.setItem('notes', JSON.stringify(notes));
        }

        function newNote() {
            const note = { id: Date.now(), title: '', content: '', createdAt: Date.now() };
            notes.unshift(note);
            currentNoteId = note.id;
            saveToStorage();
            showEditor(note);
        }

        function showEditor(note) {
            document.getElementById('notesView').style.display = 'none';
            document.getElementById('editorView').style.display = 'block';
            document.getElementById('noteTitle').value = note.title || '';
            document.getElementById('noteContent').value = note.content || '';
        }

        function showNotes() {
            document.getElementById('notesView').style.display = 'block';
            document.getElementById('editorView').style.display = 'none';
            currentNoteId = null;
            renderNotes();
        }

        function saveNote() {
            const note = notes.find(n => n.id === currentNoteId);
            if (note) {
                note.title = document.getElementById('noteTitle').value;
                note.content = document.getElementById('noteContent').value;
                note.updatedAt = Date.now();
                saveToStorage();
                showNotes();
            }
        }

        function deleteCurrentNote() {
            if (confirm('Delete this note?')) {
                notes = notes.filter(n => n.id !== currentNoteId);
                saveToStorage();
                showNotes();
            }
        }

        function renderNotes() {
            const list = document.getElementById('noteList');
            list.innerHTML = notes.map(n => `
                <div class="note-item" onclick="editNote(${'$'}{n.id})">
                    <h3>${'$'}{n.title || 'Untitled'}</h3>
                    <p>${'$'}{n.content || 'No content'}</p>
                    <div class="date">${'$'}{new Date(n.createdAt).toLocaleDateString()}</div>
                </div>
            `).join('');
        }

        function editNote(id) {
            currentNoteId = id;
            const note = notes.find(n => n.id === id);
            if (note) showEditor(note);
        }

        renderNotes();
    </script>
</body>
</html>""",
        css = "",
        javascript = ""
    )

    val ALL = listOf(BLANK, TODO_LIST, CALCULATOR, NOTE_TAKER)

    fun getById(id: String): MiniAppTemplate? = ALL.find { it.id == id }
}
