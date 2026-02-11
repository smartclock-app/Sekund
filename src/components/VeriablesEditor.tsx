import useRouter from "@/hooks/useRouter";
import useVariablesStore from "@/hooks/useVariablesStore";
import { useState } from "react";

const VariablesEditor = () => {
  const routerStore = useRouter();
  const [editedVariables, setEditedVariables] = useState(useVariablesStore.getState().variables);

  return (
    <div className="editor" style={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <nav style={{ padding: "1rem", display: "flex", justifyContent: "space-between" }}>
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
      <textarea
        style={{ flex: 1 }}
        autoCapitalize="off"
        autoComplete="off"
        autoCorrect="off"
        spellCheck="false"
        value={editedVariables}
        onChange={e => setEditedVariables(e.target.value)}
      />
    </div>
  );
};

export default VariablesEditor;
