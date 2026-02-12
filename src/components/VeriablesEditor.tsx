import templateCss from "@/assets/variables.css?raw";
import useRouter from "@/hooks/useRouter";
import useVariablesStore from "@/hooks/useVariablesStore";
import { Editor, useMonaco } from "@monaco-editor/react";
import { editor } from "monaco-editor";
import { useEffect, useRef, useState } from "react";

function extractCssVariables(cssContent: string): string[] {
  const varRegex = /--[\w-]+/g;
  const matches = cssContent.match(varRegex) || [];
  return [...new Set(matches)]; // Remove duplicates
}

const VariablesEditor = () => {
  const routerStore = useRouter();
  const [editedVariables, setEditedVariables] = useState(useVariablesStore.getState().variables);

  const monaco = useMonaco();
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  useEffect(() => {
    if (!monaco) return;

    // Extract available variables from template
    const availableVars = extractCssVariables(templateCss);

    // Register custom completion provider for CSS
    const disposable = monaco.languages.registerCompletionItemProvider("css", {
      provideCompletionItems: (model, position) => {
        // Get the word being typed
        const word = model.getWordUntilPosition(position);

        // Create suggestions for available variables
        const suggestions = availableVars.map((varName, index) => ({
          label: varName,
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: varName,
          range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn,
          },
          sortText: String(index).padStart(4, "0"),
          documentation: `CSS variable: ${varName}`,
        }));

        return { suggestions };
      },
      triggerCharacters: ["-"], // Trigger when typing after --
    });

    return () => disposable.dispose();
  }, [monaco, templateCss]);

  const handleEditorMount = (editor: editor.IStandaloneCodeEditor) => {
    editorRef.current = editor;
  };

  return (
    <div className="editor" style={{ height: "100%" }}>
      <nav
        style={{
          padding: "1rem",
          display: "flex",
          justifyContent: "space-between",
          backgroundColor: "#f0f0f0",
          borderBottom: "1px solid #ccc",
        }}
      >
        <button onClick={() => routerStore.goBack()}>Back</button>
        <button
          onClick={async () => {
            await useVariablesStore.getState().setVariables(editedVariables);
            window.location.reload();
          }}
        >
          Save
        </button>
      </nav>

      <Editor
        height="100%"
        defaultLanguage="css"
        value={editedVariables}
        onChange={value => setEditedVariables(value || "")}
        onMount={handleEditorMount}
      />
    </div>
  );
};

export default VariablesEditor;
